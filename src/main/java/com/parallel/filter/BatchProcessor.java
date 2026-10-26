package com.parallel.filter;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * High-performance batch image processor implementing three distinct parallel architectural patterns:
 * 1. DYNAMIC_QUEUE: Atomic index-based work-stealing for dynamic load balancing.
 * 2. STATIC_PARTITION: Deterministic chunk partitioning with zero inter-thread synchronization overhead.
 * 3. PIPELINED_IO_COMPUTE: Producer-Consumer pipeline decoupling I/O disk bounds from CPU Sobel convolution.
 */
public class BatchProcessor {

    public enum ExecutionStrategy {
        DYNAMIC_QUEUE("Dynamic Atomic Queue"),
        STATIC_PARTITION("Static Chunk Partitioning"),
        PIPELINED_PRODUCER_CONSUMER("Pipelined (I/O & Compute Decoupled)");

        private final String displayName;

        ExecutionStrategy(String displayName) {
            this.displayName = displayName;
        }

        @Override
        public String toString() {
            return displayName;
        }
    }

    @FunctionalInterface
    public interface ProgressListener {
        void onProgress(int completed, int total);
    }

    // -------------------------------------------------------------
    // Baseline: Sequential Processing (Single-Thread)
    // -------------------------------------------------------------
    public static long processSequential(List<File> imageFiles, File outputDir, ProgressListener listener) throws IOException {
        long startTime = System.currentTimeMillis();
        int total = imageFiles.size();

        for (int i = 0; i < total; i++) {
            File imageFile = imageFiles.get(i);
            processSingleImageSequential(imageFile, outputDir);
            if (listener != null) {
                listener.onProgress(i + 1, total);
            }
        }

        return System.currentTimeMillis() - startTime;
    }

    // -------------------------------------------------------------
    // Master Parallel Dispatcher
    // -------------------------------------------------------------
    public static long processParallel(List<File> imageFiles, File outputDir, int numThreads, ExecutionStrategy strategy, ProgressListener listener) throws InterruptedException, IOException {
        if (imageFiles.isEmpty()) return 0;

        switch (strategy) {
            case STATIC_PARTITION:
                return executeStaticPartitioning(imageFiles, outputDir, numThreads, listener);
            case PIPELINED_PRODUCER_CONSUMER:
                return executePipelined(imageFiles, outputDir, numThreads, listener);
            case DYNAMIC_QUEUE:
            default:
                return executeDynamicQueue(imageFiles, outputDir, numThreads, listener);
        }
    }

    // -------------------------------------------------------------
    // Strategy 1: Dynamic Atomic Task Queue (Load-Balanced)
    // -------------------------------------------------------------
    private static long executeDynamicQueue(List<File> imageFiles, File outputDir, int numThreads, ProgressListener listener) throws InterruptedException {
        long startTime = System.currentTimeMillis();
        int totalImages = imageFiles.size();

        AtomicInteger nextImageIndex = new AtomicInteger(0);
        AtomicInteger completedCount = new AtomicInteger(0);
        List<Thread> workers = new ArrayList<>();
        int activeThreads = Math.min(numThreads, totalImages);

        for (int i = 0; i < activeThreads; i++) {
            Thread worker = new Thread(() -> {
                while (true) {
                    int index = nextImageIndex.getAndIncrement();
                    if (index >= totalImages) break;

                    File imageFile = imageFiles.get(index);
                    try {
                        processSingleImageSequential(imageFile, outputDir);
                    } catch (Throwable t) {
                        System.err.println("Dynamic Queue Error: " + imageFile.getName() + " -> " + t.getMessage());
                    }

                    int done = completedCount.incrementAndGet();
                    if (listener != null) listener.onProgress(done, totalImages);
                }
            }, "DynamicWorker-" + i);

            workers.add(worker);
            worker.start();
        }

        for (Thread worker : workers) worker.join();
        return System.currentTimeMillis() - startTime;
    }

    // -------------------------------------------------------------
    // Strategy 2: Static Domain Partitioning (Zero Inter-Thread Sync)
    // -------------------------------------------------------------
    private static long executeStaticPartitioning(List<File> imageFiles, File outputDir, int numThreads, ProgressListener listener) throws InterruptedException {
        long startTime = System.currentTimeMillis();
        int totalImages = imageFiles.size();

        AtomicInteger completedCount = new AtomicInteger(0);
        List<Thread> workers = new ArrayList<>();
        int activeThreads = Math.min(numThreads, totalImages);

        int chunkSize = (totalImages + activeThreads - 1) / activeThreads;

        for (int i = 0; i < activeThreads; i++) {
            final int startIdx = i * chunkSize;
            final int endIdx = Math.min(startIdx + chunkSize, totalImages);

            if (startIdx >= endIdx) break;

            Thread worker = new Thread(() -> {
                for (int j = startIdx; j < endIdx; j++) {
                    File imageFile = imageFiles.get(j);
                    try {
                        processSingleImageSequential(imageFile, outputDir);
                    } catch (Throwable t) {
                        System.err.println("Static Worker Error: " + imageFile.getName() + " -> " + t.getMessage());
                    }

                    int done = completedCount.incrementAndGet();
                    if (listener != null) listener.onProgress(done, totalImages);
                }
            }, "StaticWorker-" + i);

            workers.add(worker);
            worker.start();
        }

        for (Thread worker : workers) worker.join();
        return System.currentTimeMillis() - startTime;
    }

    // -------------------------------------------------------------
    // Strategy 3: Pipelined Producer-Consumer Architecture
    // -------------------------------------------------------------
    private static class RawImageTask {
        final File file;
        final BufferedImage image;

        RawImageTask(File file, BufferedImage image) {
            this.file = file;
            this.image = image;
        }
    }

    private static class ProcessedImageTask {
        final File file;
        final BufferedImage edgeImage;

        ProcessedImageTask(File file, BufferedImage edgeImage) {
            this.file = file;
            this.edgeImage = edgeImage;
        }
    }

    private static final RawImageTask POISON_PILL_RAW = new RawImageTask(null, null);
    private static final ProcessedImageTask POISON_PILL_PROCESSED = new ProcessedImageTask(null, null);

    private static long executePipelined(List<File> imageFiles, File outputDir, int numThreads, ProgressListener listener) throws InterruptedException {
        long startTime = System.currentTimeMillis();
        int totalImages = imageFiles.size();

        // Bounded queues to prevent out-of-memory overhead
        BlockingQueue<RawImageTask> computeQueue = new ArrayBlockingQueue<>(16);
        BlockingQueue<ProcessedImageTask> writeQueue = new ArrayBlockingQueue<>(16);
        AtomicInteger completedCount = new AtomicInteger(0);

        int computeThreadsCount = Math.max(1, numThreads);

        // 1. Producer Thread: Dedicated Disk Reader
        Thread readerThread = new Thread(() -> {
            try {
                for (File file : imageFiles) {
                    BufferedImage img = ImageIO.read(file);
                    if (img != null) {
                        computeQueue.put(new RawImageTask(file, img));
                    }
                }
            } catch (Exception e) {
                System.err.println("Reader Pipeline Error: " + e.getMessage());
            } finally {
                // Signal compute workers to terminate
                for (int i = 0; i < computeThreadsCount; i++) {
                    try { computeQueue.put(POISON_PILL_RAW); } catch (InterruptedException ignored) {}
                }
            }
        }, "PipelineReader");

        // 2. Consumer Thread: Dedicated Disk Writer
        Thread writerThread = new Thread(() -> {
            try {
                while (true) {
                    ProcessedImageTask task = writeQueue.take();
                    if (task == POISON_PILL_PROCESSED) break;

                    File outputFile = new File(outputDir, "edge_" + task.file.getName());
                    try {
                        ImageIO.write(task.edgeImage, "jpg", outputFile);
                    } finally {
                        task.edgeImage.flush();
                    }

                    int done = completedCount.incrementAndGet();
                    if (listener != null) listener.onProgress(done, totalImages);
                }
            } catch (Exception e) {
                System.err.println("Writer Pipeline Error: " + e.getMessage());
            }
        }, "PipelineWriter");

        // 3. Worker Threads: Pure CPU Compute (Sobel Convolution)
        List<Thread> computeWorkers = new ArrayList<>();
        AtomicInteger activeComputers = new AtomicInteger(computeThreadsCount);

        for (int i = 0; i < computeThreadsCount; i++) {
            Thread worker = new Thread(() -> {
                try {
                    while (true) {
                        RawImageTask task = computeQueue.take();
                        if (task == POISON_PILL_RAW) break;

                        BufferedImage edgeImg = SobelFilter.applySequential(task.image);
                        task.image.flush(); // Free input memory immediately

                        writeQueue.put(new ProcessedImageTask(task.file, edgeImg));
                    }
                } catch (Exception e) {
                    System.err.println("Compute Worker Error: " + e.getMessage());
                } finally {
                    if (activeComputers.decrementAndGet() == 0) {
                        try { writeQueue.put(POISON_PILL_PROCESSED); } catch (InterruptedException ignored) {}
                    }
                }
            }, "ComputeWorker-" + i);

            computeWorkers.add(worker);
        }

        // Launch all stages concurrently
        readerThread.start();
        writerThread.start();
        for (Thread w : computeWorkers) w.start();

        // Await full pipeline completion
        readerThread.join();
        for (Thread w : computeWorkers) w.join();
        writerThread.join();

        return System.currentTimeMillis() - startTime;
    }

    private static void processSingleImageSequential(File imageFile, File outputDir) throws IOException {
        BufferedImage inputImage = null;
        BufferedImage edgeImage = null;
        try {
            inputImage = ImageIO.read(imageFile);
            if (inputImage == null) return;

            edgeImage = SobelFilter.applySequential(inputImage);

            File outputFile = new File(outputDir, "edge_" + imageFile.getName());
            ImageIO.write(edgeImage, "jpg", outputFile);
        } finally {
            if (inputImage != null) inputImage.flush();
            if (edgeImage != null) edgeImage.flush();
        }
    }
}

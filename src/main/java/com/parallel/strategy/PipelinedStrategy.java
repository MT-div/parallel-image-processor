package com.parallel.strategy;

import com.parallel.filter.SobelFilter;
import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.File;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.atomic.AtomicInteger;

public class PipelinedStrategy implements BatchStrategy {

    private static class RawTask {
        final File file;
        final BufferedImage img;
        RawTask(File file, BufferedImage img) { this.file = file; this.img = img; }
    }

    private static class ProcessedTask {
        final File file;
        final BufferedImage edge;
        ProcessedTask(File file, BufferedImage edge) { this.file = file; this.edge = edge; }
    }

    private static final RawTask POISON_RAW = new RawTask(null, null);
    private static final ProcessedTask POISON_PROCESSED = new ProcessedTask(null, null);

    @Override
    public String getName() {
        return "Pipelined (I/O & Compute Decoupled)";
    }

    @Override
    public long execute(List<File> images, File outputDir, int threads, ProgressCallback callback) throws Exception {
        long startTime = System.currentTimeMillis();
        int total = images.size();

        BlockingQueue<RawTask> computeQueue = new ArrayBlockingQueue<>(16);
        BlockingQueue<ProcessedTask> writeQueue = new ArrayBlockingQueue<>(16);
        AtomicInteger completed = new AtomicInteger(0);

        int computeThreads = Math.max(1, threads);

        // Reader Thread
        Thread reader = new Thread(() -> {
            try {
                for (File f : images) {
                    BufferedImage b = ImageIO.read(f);
                    if (b != null) computeQueue.put(new RawTask(f, b));
                }
            } catch (Exception ignored) {
            } finally {
                for (int i = 0; i < computeThreads; i++) {
                    try { computeQueue.put(POISON_RAW); } catch (InterruptedException ignored) {}
                }
            }
        }, "PipeReader");

        // Writer Thread
        Thread writer = new Thread(() -> {
            try {
                while (true) {
                    ProcessedTask pt = writeQueue.take();
                    if (pt == POISON_PROCESSED) break;
                    File out = new File(outputDir, "edge_" + pt.file.getName());
                    try {
                        ImageIO.write(pt.edge, "jpg", out);
                    } finally {
                        pt.edge.flush();
                    }
                    int done = completed.incrementAndGet();
                    if (callback != null) callback.onProgress(done, total);
                }
            } catch (Exception ignored) {}
        }, "PipeWriter");

        // Compute Worker Threads
        List<Thread> workers = new ArrayList<>();
        AtomicInteger activeWorkers = new AtomicInteger(computeThreads);

        for (int i = 0; i < computeThreads; i++) {
            Thread w = new Thread(() -> {
                try {
                    while (true) {
                        RawTask rt = computeQueue.take();
                        if (rt == POISON_RAW) break;
                        BufferedImage edge = SobelFilter.applySequential(rt.img);
                        rt.img.flush();
                        writeQueue.put(new ProcessedTask(rt.file, edge));
                    }
                } catch (Exception ignored) {
                } finally {
                    if (activeWorkers.decrementAndGet() == 0) {
                        try { writeQueue.put(POISON_PROCESSED); } catch (InterruptedException ignored) {}
                    }
                }
            }, "PipeCompute-" + i);
            workers.add(w);
        }

        reader.start();
        writer.start();
        for (Thread w : workers) w.start();

        reader.join();
        for (Thread w : workers) w.join();
        writer.join();

        return System.currentTimeMillis() - startTime;
    }
}

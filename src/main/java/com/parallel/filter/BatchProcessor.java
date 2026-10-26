package com.parallel.filter;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Handles batch processing of multiple images using both sequential 
 * and adaptive multi-threaded strategies without using ExecutorService.
 * Enhanced with explicit memory cleanup to prevent Java heap space OutOfMemoryError.
 */
public class BatchProcessor {

    @FunctionalInterface
    public interface ProgressListener {
        void onProgress(int completed, int total);
    }

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

    public static long processParallel(List<File> imageFiles, File outputDir, int numThreads, ProgressListener listener) throws InterruptedException, IOException {
        long startTime = System.currentTimeMillis();
        int totalImages = imageFiles.size();

        if (totalImages == 0) return 0;

        if (totalImages < numThreads && totalImages <= 2) {
            int completed = 0;
            for (File imageFile : imageFiles) {
                processSingleImageParallel(imageFile, outputDir, numThreads);
                completed++;
                if (listener != null) {
                    listener.onProgress(completed, totalImages);
                }
            }
        } else {
            AtomicInteger nextImageIndex = new AtomicInteger(0);
            AtomicInteger completedCount = new AtomicInteger(0);
            List<Thread> workers = new ArrayList<>();

            int activeThreads = Math.min(numThreads, totalImages);

            for (int i = 0; i < activeThreads; i++) {
                Thread worker = new Thread(() -> {
                    while (true) {
                        int index = nextImageIndex.getAndIncrement();
                        if (index >= totalImages) {
                            break;
                        }

                        File imageFile = imageFiles.get(index);
                        try {
                            processSingleImageSequential(imageFile, outputDir);
                        } catch (Throwable t) {
                            System.err.println("Error processing image: " + imageFile.getName() + " -> " + t.getMessage());
                        }

                        int done = completedCount.incrementAndGet();
                        if (listener != null) {
                            listener.onProgress(done, totalImages);
                        }
                    }
                }, "WorkerThread-" + i);

                workers.add(worker);
            }

            for (Thread worker : workers) {
                worker.start();
            }

            for (Thread worker : workers) {
                worker.join();
            }
        }

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
            // Explicitly release native image raster memory buffer
            if (inputImage != null) inputImage.flush();
            if (edgeImage != null) edgeImage.flush();
        }
    }

    private static void processSingleImageParallel(File imageFile, File outputDir, int threadsPerImage) throws IOException, InterruptedException {
        BufferedImage inputImage = null;
        BufferedImage edgeImage = null;
        try {
            inputImage = ImageIO.read(imageFile);
            if (inputImage == null) return;

            edgeImage = SobelFilter.applyParallel(inputImage, threadsPerImage);

            File outputFile = new File(outputDir, "edge_" + imageFile.getName());
            ImageIO.write(edgeImage, "jpg", outputFile);
        } finally {
            if (inputImage != null) inputImage.flush();
            if (edgeImage != null) edgeImage.flush();
        }
    }
}

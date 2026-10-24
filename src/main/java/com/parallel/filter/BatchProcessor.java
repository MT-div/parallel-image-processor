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
 */
public class BatchProcessor {

    /**
     * Callback interface to notify the UI for real-time progress updates.
     */
    @FunctionalInterface
    public interface ProgressListener {
        void onProgress(int completed, int total);
    }

    /**
     * Executes sequential batch processing using a single thread.
     *
     * @param imageFiles list of image files to process
     * @param outputDir  destination directory for processed images
     * @param listener   optional progress callback
     * @return total elapsed time in milliseconds
     * @throws IOException if an error occurs during file reading/writing
     */
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

    /**
     * Executes adaptive parallel batch processing.
     * Strategy:
     * - If total images <= 2, parallelize image row chunks across threads.
     * - If total images > 2, distribute full images across threads using an atomic index counter.
     *
     * @param imageFiles list of image files to process
     * @param outputDir  destination directory for processed images
     * @param numThreads requested number of concurrent worker threads
     * @param listener   optional progress callback
     * @return total elapsed time in milliseconds
     * @throws InterruptedException if a thread join is interrupted
     * @throws IOException          if an error occurs during file reading/writing
     */
    public static long processParallel(List<File> imageFiles, File outputDir, int numThreads, ProgressListener listener) throws InterruptedException, IOException {
        long startTime = System.currentTimeMillis();
        int totalImages = imageFiles.size();

        if (totalImages == 0) return 0;

        // Case A: Very few images (<= 2). Parallelize row chunks inside each image.
        if (totalImages < numThreads && totalImages <= 2) {
            int completed = 0;
            for (File imageFile : imageFiles) {
                processSingleImageParallel(imageFile, outputDir, numThreads);
                completed++;
                if (listener != null) {
                    listener.onProgress(completed, totalImages);
                }
            }
        } 
        // Case B: Multiple images. Distribute full images across worker threads.
        else {
            AtomicInteger nextImageIndex = new AtomicInteger(0);
            AtomicInteger completedCount = new AtomicInteger(0);
            List<Thread> workers = new ArrayList<>();

            int activeThreads = Math.min(numThreads, totalImages);

            for (int i = 0; i < activeThreads; i++) {
                Thread worker = new Thread(() -> {
                    while (true) {
                        // Atomic fetch next available image index to prevent race conditions
                        int index = nextImageIndex.getAndIncrement();
                        if (index >= totalImages) {
                            break;
                        }

                        File imageFile = imageFiles.get(index);
                        try {
                            processSingleImageSequential(imageFile, outputDir);
                        } catch (IOException e) {
                            System.err.println("Error processing image: " + imageFile.getName() + " -> " + e.getMessage());
                        }

                        int done = completedCount.incrementAndGet();
                        if (listener != null) {
                            listener.onProgress(done, totalImages);
                        }
                    }
                }, "WorkerThread-" + i);

                workers.add(worker);
            }

            // Launch worker threads
            for (Thread worker : workers) {
                worker.start();
            }

            // Wait for all worker threads to complete
            for (Thread worker : workers) {
                worker.join();
            }
        }

        return System.currentTimeMillis() - startTime;
    }

    /**
     * Reads an image, applies sequential Sobel filter, and writes to output.
     */
    private static void processSingleImageSequential(File imageFile, File outputDir) throws IOException {
        BufferedImage inputImage = ImageIO.read(imageFile);
        if (inputImage == null) return;

        BufferedImage edgeImage = SobelFilter.applySequential(inputImage);

        File outputFile = new File(outputDir, "edge_" + imageFile.getName());
        ImageIO.write(edgeImage, "jpg", outputFile);
    }

    /**
     * Reads an image, applies multi-threaded Sobel filter on row slices, and writes to output.
     */
    private static void processSingleImageParallel(File imageFile, File outputDir, int threadsPerImage) throws IOException, InterruptedException {
        BufferedImage inputImage = ImageIO.read(imageFile);
        if (inputImage == null) return;

        BufferedImage edgeImage = SobelFilter.applyParallel(inputImage, threadsPerImage);

        File outputFile = new File(outputDir, "edge_" + imageFile.getName());
        ImageIO.write(edgeImage, "jpg", outputFile);
    }
}

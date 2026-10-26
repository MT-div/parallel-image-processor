package com.parallel.strategy;

import javax.imageio.ImageIO;
import java.awt.Color;
import java.awt.image.BufferedImage;
import java.awt.image.DataBufferInt;
import java.io.File;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Strategy 4: High-Throughput Clustered Pipeline.
 * Eliminates single-reader starvation via multi-reader threads, multi-writer threads,
 * and direct native integer array raster processing for maximum CPU cache utilization.
 */
public class ClusteredPipelineStrategy implements BatchStrategy {

    private static final int[][] Gx = {{-1, 0, 1}, {-2, 0, 2}, {-1, 0, 1}};
    private static final int[][] Gy = {{-1, -2, -1}, {0, 0, 0}, {1, 2, 1}};

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
        return "Clustered Multi-I/O Pipeline";
    }

    @Override
    public long execute(List<File> images, File outputDir, int threads, ProgressCallback callback) throws Exception {
        long startTime = System.currentTimeMillis();
        int totalImages = images.size();

        BlockingQueue<RawTask> computeQueue = new ArrayBlockingQueue<>(32);
        BlockingQueue<ProcessedTask> writeQueue = new ArrayBlockingQueue<>(32);
        AtomicInteger completed = new AtomicInteger(0);

        int readerCount = Math.min(2, Math.max(1, totalImages / 2));
        int writerCount = Math.min(2, Math.max(1, totalImages / 2));
        int computeThreads = Math.max(1, threads);

        AtomicInteger readerIndex = new AtomicInteger(0);
        AtomicInteger activeReaders = new AtomicInteger(readerCount);
        AtomicInteger activeComputers = new AtomicInteger(computeThreads);

        // 1. Clustered Multi-Reader Threads
        List<Thread> readerThreads = new ArrayList<>();
        for (int r = 0; r < readerCount; r++) {
            Thread rt = new Thread(() -> {
                while (true) {
                    int idx = readerIndex.getAndIncrement();
                    if (idx >= totalImages) break;
                    File f = images.get(idx);
                    try {
                        BufferedImage b = ImageIO.read(f);
                        if (b != null) computeQueue.put(new RawTask(f, b));
                    } catch (Exception ignored) {}
                }
                if (activeReaders.decrementAndGet() == 0) {
                    for (int i = 0; i < computeThreads; i++) {
                        try { computeQueue.put(POISON_RAW); } catch (InterruptedException ignored) {}
                    }
                }
            }, "ClusterReader-" + r);
            readerThreads.add(rt);
            rt.start();
        }

        // 2. Clustered Multi-Writer Threads
        List<Thread> writerThreads = new ArrayList<>();
        for (int w = 0; w < writerCount; w++) {
            Thread wt = new Thread(() -> {
                try {
                    while (true) {
                        ProcessedTask pt = writeQueue.take();
                        if (pt == POISON_PROCESSED) {
                            writeQueue.put(POISON_PROCESSED); // Propagate termination to other writers
                            break;
                        }
                        File out = new File(outputDir, "edge_" + pt.file.getName());
                        try {
                            ImageIO.write(pt.edge, "jpg", out);
                        } finally {
                            pt.edge.flush();
                        }
                        int done = completed.incrementAndGet();
                        if (callback != null) callback.onProgress(done, totalImages);
                    }
                } catch (Exception ignored) {}
            }, "ClusterWriter-" + w);
            writerThreads.add(wt);
            wt.start();
        }

        // 3. Compute Threads with Direct Raster Math (Bypassing slow getRGB calls)
        List<Thread> computeWorkers = new ArrayList<>();
        for (int i = 0; i < computeThreads; i++) {
            Thread cw = new Thread(() -> {
                try {
                    while (true) {
                        RawTask task = computeQueue.take();
                        if (task == POISON_RAW) break;

                        BufferedImage edgeImg = applyFastDirectSobel(task.img);
                        task.img.flush();

                        writeQueue.put(new ProcessedTask(task.file, edgeImg));
                    }
                } catch (Exception ignored) {
                } finally {
                    if (activeComputers.decrementAndGet() == 0) {
                        try { writeQueue.put(POISON_PROCESSED); } catch (InterruptedException ignored) {}
                    }
                }
            }, "ClusterCompute-" + i);
            computeWorkers.add(cw);
            cw.start();
        }

        // Wait for all stages
        for (Thread rt : readerThreads) rt.join();
        for (Thread cw : computeWorkers) cw.join();
        for (Thread wt : writerThreads) wt.join();

        return System.currentTimeMillis() - startTime;
    }

    /**
     * Fast Sobel Filter operating directly on raw integer memory buffers
     */
    private static BufferedImage applyFastDirectSobel(BufferedImage src) {
        int width = src.getWidth();
        int height = src.getHeight();

        BufferedImage formattedSrc = src;
        if (src.getType() != BufferedImage.TYPE_INT_RGB) {
            formattedSrc = new BufferedImage(width, height, BufferedImage.TYPE_INT_RGB);
            formattedSrc.getGraphics().drawImage(src, 0, 0, null);
        }

        int[] srcPixels = ((DataBufferInt) formattedSrc.getRaster().getDataBuffer()).getData();
        BufferedImage dest = new BufferedImage(width, height, BufferedImage.TYPE_INT_RGB);
        int[] destPixels = ((DataBufferInt) dest.getRaster().getDataBuffer()).getData();

        for (int y = 1; y < height - 1; y++) {
            int rowOffset = y * width;
            for (int x = 1; x < width - 1; x++) {
                int sumX = 0;
                int sumY = 0;

                for (int ky = -1; ky <= 1; ky++) {
                    int kRow = (y + ky) * width;
                    for (int kx = -1; kx <= 1; kx++) {
                        int pixel = srcPixels[kRow + (x + kx)];
                        int r = (pixel >> 16) & 0xFF;
                        int g = (pixel >> 8) & 0xFF;
                        int b = pixel & 0xFF;
                        int gray = (r * 77 + g * 150 + b * 29) >> 8; // Ultra-fast bitwise grayscale

                        sumX += Gx[ky + 1][kx + 1] * gray;
                        sumY += Gy[ky + 1][kx + 1] * gray;
                    }
                }

                int magnitude = (int) Math.sqrt((sumX * sumX) + (sumY * sumY));
                magnitude = Math.min(255, Math.max(0, magnitude));
                destPixels[rowOffset + x] = (magnitude << 16) | (magnitude << 8) | magnitude;
            }
        }

        if (formattedSrc != src) formattedSrc.flush();
        return dest;
    }
}

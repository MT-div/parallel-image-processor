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

public class ClusteredPipelineStrategy implements BatchStrategy {

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

        List<Thread> writerThreads = new ArrayList<>();
        for (int w = 0; w < writerCount; w++) {
            Thread wt = new Thread(() -> {
                try {
                    while (true) {
                        ProcessedTask pt = writeQueue.take();
                        if (pt == POISON_PROCESSED) {
                            writeQueue.put(POISON_PROCESSED);
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

        List<Thread> computeWorkers = new ArrayList<>();
        for (int i = 0; i < computeThreads; i++) {
            Thread cw = new Thread(() -> {
                try {
                    while (true) {
                        RawTask task = computeQueue.take();
                        if (task == POISON_RAW) break;

                        BufferedImage edgeImg = SobelFilter.applySequential(task.img);
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

        for (Thread rt : readerThreads) rt.join();
        for (Thread cw : computeWorkers) cw.join();
        for (Thread wt : writerThreads) wt.join();

        return System.currentTimeMillis() - startTime;
    }
}

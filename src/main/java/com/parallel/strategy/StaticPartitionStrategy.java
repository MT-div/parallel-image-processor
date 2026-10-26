package com.parallel.strategy;

import java.io.File;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

public class StaticPartitionStrategy implements BatchStrategy {
    @Override
    public String getName() {
        return "Static Chunk Partitioning";
    }

    @Override
    public long execute(List<File> images, File outputDir, int threads, ProgressCallback callback) throws Exception {
        long startTime = System.currentTimeMillis();
        int total = images.size();
        AtomicInteger completed = new AtomicInteger(0);

        List<Thread> workers = new ArrayList<>();
        int activeThreads = Math.min(threads, total);
        int chunkSize = (total + activeThreads - 1) / activeThreads;

        for (int i = 0; i < activeThreads; i++) {
            final int start = i * chunkSize;
            final int end = Math.min(start + chunkSize, total);
            if (start >= end) break;

            Thread t = new Thread(() -> {
                for (int j = start; j < end; j++) {
                    try {
                        SequentialStrategy.processSingleImage(images.get(j), outputDir);
                    } catch (Exception e) {
                        System.err.println("Static Worker Error: " + e.getMessage());
                    }
                    int done = completed.incrementAndGet();
                    if (callback != null) callback.onProgress(done, total);
                }
            }, "StaticWorker-" + i);
            workers.add(t);
            t.start();
        }

        for (Thread t : workers) t.join();
        return System.currentTimeMillis() - startTime;
    }
}

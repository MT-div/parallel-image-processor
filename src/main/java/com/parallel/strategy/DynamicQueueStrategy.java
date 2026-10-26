package com.parallel.strategy;

import java.io.File;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

public class DynamicQueueStrategy implements BatchStrategy {
    @Override
    public String getName() {
        return "Dynamic Atomic Queue";
    }

    @Override
    public long execute(List<File> images, File outputDir, int threads, ProgressCallback callback) throws Exception {
        long startTime = System.currentTimeMillis();
        int total = images.size();
        AtomicInteger nextIdx = new AtomicInteger(0);
        AtomicInteger completed = new AtomicInteger(0);

        List<Thread> workers = new ArrayList<>();
        int activeThreads = Math.min(threads, total);

        for (int i = 0; i < activeThreads; i++) {
            Thread t = new Thread(() -> {
                while (true) {
                    int idx = nextIdx.getAndIncrement();
                    if (idx >= total) break;

                    try {
                        SequentialStrategy.processSingleImage(images.get(idx), outputDir);
                    } catch (Exception e) {
                        System.err.println("Dynamic Queue Error: " + e.getMessage());
                    }

                    int done = completed.incrementAndGet();
                    if (callback != null) callback.onProgress(done, total);
                }
            }, "DynamicWorker-" + i);
            workers.add(t);
            t.start();
        }

        for (Thread t : workers) t.join();
        return System.currentTimeMillis() - startTime;
    }
}

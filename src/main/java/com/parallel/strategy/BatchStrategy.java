package com.parallel.strategy;

import java.io.File;
import java.util.List;

public interface BatchStrategy {
    @FunctionalInterface
    interface ProgressCallback {
        void onProgress(int completed, int total);
    }

    String getName();
    long execute(List<File> images, File outputDir, int threads, ProgressCallback callback) throws Exception;
}

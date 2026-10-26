package com.parallel.strategy;

import com.parallel.filter.SobelFilter;
import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.File;
import java.util.List;
import java.util.concurrent.Semaphore;

public class SequentialStrategy implements BatchStrategy {

    // Maximum concurrent images allowed in RAM to strictly prevent OutOfMemoryError
    // regardless of the JVM heap size on the evaluator's machine.
    public static final Semaphore MEMORY_THROTTLE = new Semaphore(4);

    @Override
    public String getName() {
        return "Sequential (Baseline)";
    }

    @Override
    public long execute(List<File> images, File outputDir, int threads, ProgressCallback callback) throws Exception {
        long startTime = System.currentTimeMillis();
        int total = images.size();

        for (int i = 0; i < total; i++) {
            File imgFile = images.get(i);
            processSingleImage(imgFile, outputDir);
            if (callback != null) callback.onProgress(i + 1, total);
        }

        return System.currentTimeMillis() - startTime;
    }

    public static void processSingleImage(File imgFile, File outputDir) throws Exception {
        MEMORY_THROTTLE.acquire(); // Throttle memory access safely across all worker threads
        BufferedImage input = null;
        BufferedImage edge = null;
        try {
            input = ImageIO.read(imgFile);
            if (input == null) return;
            
            edge = SobelFilter.applySequential(input);
            File out = new File(outputDir, "edge_" + imgFile.getName());
            ImageIO.write(edge, "jpg", out);
        } finally {
            if (input != null) input.flush();
            if (edge != null) edge.flush();
            MEMORY_THROTTLE.release(); // Immediately release slot for subsequent waiting threads
        }
    }
}

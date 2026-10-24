package com.parallel;

import com.parallel.filter.BatchProcessor;
import junit.framework.TestCase;

import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import javax.imageio.ImageIO;

public class BenchmarkTest extends TestCase {

    private static final String INPUT_DIR_PATH = "temp_benchmark_input";
    private static final String OUTPUT_DIR_PATH = "temp_benchmark_output";
    private static final int IMAGE_COUNT = 16;
    private static final int IMAGE_WIDTH = 1920;
    private static final int IMAGE_HEIGHT = 1080;

    private List<File> testFiles;
    private File outputDir;

    // Added main method to allow direct execution as a standard application
    public static void main(String[] args) throws Exception {
        BenchmarkTest benchmark = new BenchmarkTest();
        benchmark.setUp();
        try {
            benchmark.testRunCompleteBenchmark();
        } finally {
            benchmark.tearDown();
        }
    }

    @Override
    public void setUp() throws Exception {
        super.setUp();
        File inputDir = new File(INPUT_DIR_PATH);
        outputDir = new File(OUTPUT_DIR_PATH);

        if (!inputDir.exists()) inputDir.mkdirs();
        if (!outputDir.exists()) outputDir.mkdirs();

        testFiles = new ArrayList<>();

        for (int i = 0; i < IMAGE_COUNT; i++) {
            File imgFile = new File(inputDir, "sample_" + i + ".jpg");
            if (!imgFile.exists()) {
                BufferedImage img = new BufferedImage(IMAGE_WIDTH, IMAGE_HEIGHT, BufferedImage.TYPE_INT_RGB);
                Graphics2D g = img.createGraphics();
                g.setColor(new Color((i * 45) % 255, (i * 75) % 255, (i * 105) % 255));
                g.fillRect(0, 0, IMAGE_WIDTH, IMAGE_HEIGHT);

                g.setColor(Color.WHITE);
                for (int d = 0; d < IMAGE_WIDTH; d += 80) {
                    g.drawLine(d, 0, IMAGE_WIDTH - d, IMAGE_HEIGHT);
                }
                g.dispose();
                ImageIO.write(img, "jpg", imgFile);
            }
            testFiles.add(imgFile);
        }
    }

    public void testRunCompleteBenchmark() throws Exception {
        System.out.println("==========================================================================");
        System.out.println("  PARALLEL IMAGE PROCESSING BENCHMARK (Batch Size: " + IMAGE_COUNT + " Images @ 1080p)");
        System.out.println("  Logical CPU Cores Available: " + Runtime.getRuntime().availableProcessors());
        System.out.println("==========================================================================");

        System.out.println("-> Running Sequential Processing (1 Thread Baseline)...");
        long seqTime = BatchProcessor.processSequential(testFiles, outputDir, null);
        System.out.printf("Baseline Sequential Time (T_seq): %d ms\n\n", seqTime);

        int[] threadCounts = {1, 2, 4, 8, 16, 32, 64};

        System.out.printf("%-10s | %-16s | %-12s | %-12s\n", "Threads(N)", "Execution Time", "Speedup (S)", "Efficiency (E)");
        System.out.println("--------------------------------------------------------------------------");

        for (int threads : threadCounts) {
            long parTime = BatchProcessor.processParallel(testFiles, outputDir, threads, null);
            double speedup = (double) seqTime / (parTime > 0 ? parTime : 1);
            double efficiency = speedup / threads;

            System.out.printf("%-10d | %-14d ms | %-10.2fx | %-10.2f\n", threads, parTime, speedup, efficiency);
        }

        System.out.println("==========================================================================");
        assertTrue(seqTime > 0);
    }

    @Override
    public void tearDown() throws Exception {
        super.tearDown();
        deleteDirectory(new File(INPUT_DIR_PATH));
        deleteDirectory(new File(OUTPUT_DIR_PATH));
    }

    private void deleteDirectory(File dir) {
        File[] files = dir.listFiles();
        if (files != null) {
            for (File f : files) {
                f.delete();
            }
        }
        dir.delete();
    }
}

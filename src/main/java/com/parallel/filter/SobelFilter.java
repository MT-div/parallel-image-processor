package com.parallel.filter;

import java.awt.Color;
import java.awt.image.BufferedImage;
import java.util.ArrayList;
import java.util.List;

public class SobelFilter {

    // ??????? ???????? ???????? (Kernels)
    private static final int[][] Gx = {
        {-1, 0, 1},
        {-2, 0, 2},
        {-1, 0, 1}
    };

    private static final int[][] Gy = {
        {-1, -2, -1},
        { 0,  0,  0},
        { 1,  2,  1}
    };

    // 1. ?????? ????????? (???? ??? ??? ???? ??? ?????? ??? ?????)
    public static BufferedImage applySequential(BufferedImage src) {
        int width = src.getWidth();
        int height = src.getHeight();
        BufferedImage dest = new BufferedImage(width, height, BufferedImage.TYPE_INT_RGB);

        processRows(src, dest, 1, height - 1);
        return dest;
    }

    // 2. ?????? ???????? (???? ???? ?????? ??? ??? ???? ?? ??????)
    public static BufferedImage applyParallel(BufferedImage src, int numThreads) throws InterruptedException {
        int width = src.getWidth();
        int height = src.getHeight();
        BufferedImage dest = new BufferedImage(width, height, BufferedImage.TYPE_INT_RGB);

        List<Thread> threads = new ArrayList<>();
        int effectiveHeight = height - 2; // ?????? ?????? ???????
        int chunkSize = (effectiveHeight + numThreads - 1) / numThreads;

        for (int i = 0; i < numThreads; i++) {
            final int startY = 1 + (i * chunkSize);
            final int endY = Math.min(startY + chunkSize, height - 1);

            if (startY >= endY) break;

            Thread t = new Thread(() -> processRows(src, dest, startY, endY));
            threads.add(t);
            t.start();
        }

        // ?????? ?????? ???? ??????
        for (Thread t : threads) {
            t.join();
        }

        return dest;
    }

    // ?????? ???????? ??????? ????? ????? ?? ??????
    private static void processRows(BufferedImage src, BufferedImage dest, int startY, int endY) {
        int width = src.getWidth();

        for (int y = startY; y < endY; y++) {
            for (int x = 1; x < width - 1; x++) {
                int sumX = 0;
                int sumY = 0;

                // ????? ???? 3x3
                for (int ky = -1; ky <= 1; ky++) {
                    for (int kx = -1; kx <= 1; kx++) {
                        int rgb = src.getRGB(x + kx, y + ky);
                        int r = (rgb >> 16) & 0xFF;
                        int g = (rgb >> 8) & 0xFF;
                        int b = rgb & 0xFF;
                        // ????? ????? ??? ???? ????? (Luminance)
                        int gray = (int) (0.299 * r + 0.587 * g + 0.114 * b);

                        sumX += Gx[ky + 1][kx + 1] * gray;
                        sumY += Gy[ky + 1][kx + 1] * gray;
                    }
                }

                int magnitude = (int) Math.sqrt((sumX * sumX) + (sumY * sumY));
                magnitude = Math.min(255, Math.max(0, magnitude)); // ??? ?????? ??? 0 ? 255

                int edgePixel = new Color(magnitude, magnitude, magnitude).getRGB();
                dest.setRGB(x, y, edgePixel);
            }
        }
    }
}

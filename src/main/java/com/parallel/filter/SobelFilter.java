package com.parallel.filter;

import java.awt.image.BufferedImage;
import java.awt.image.DataBufferInt;
import java.util.ArrayList;
import java.util.List;

/**
 * Standardized High-Performance Sobel Filter Kernel.
 * Operates strictly on direct native integer array buffers (Zero getRGB calls)
 * to provide a 100% fair scientific baseline across all concurrency strategies.
 */
public class SobelFilter {

    private static final int[][] Gx = {{-1, 0, 1}, {-2, 0, 2}, {-1, 0, 1}};
    private static final int[][] Gy = {{-1, -2, -1}, {0, 0, 0}, {1, 2, 1}};

    public static BufferedImage applySequential(BufferedImage src) {
        int width = src.getWidth();
        int height = src.getHeight();

        BufferedImage formattedSrc = ensureIntRgb(src);
        int[] srcPixels = ((DataBufferInt) formattedSrc.getRaster().getDataBuffer()).getData();

        BufferedImage dest = new BufferedImage(width, height, BufferedImage.TYPE_INT_RGB);
        int[] destPixels = ((DataBufferInt) dest.getRaster().getDataBuffer()).getData();

        processRowRange(srcPixels, destPixels, width, 1, height - 1);

        if (formattedSrc != src) formattedSrc.flush();
        return dest;
    }

    public static BufferedImage applyParallel(BufferedImage src, int numThreads) throws InterruptedException {
        int width = src.getWidth();
        int height = src.getHeight();

        BufferedImage formattedSrc = ensureIntRgb(src);
        int[] srcPixels = ((DataBufferInt) formattedSrc.getRaster().getDataBuffer()).getData();

        BufferedImage dest = new BufferedImage(width, height, BufferedImage.TYPE_INT_RGB);
        int[] destPixels = ((DataBufferInt) dest.getRaster().getDataBuffer()).getData();

        List<Thread> threads = new ArrayList<>();
        int effectiveHeight = height - 2;
        int chunkSize = (effectiveHeight + numThreads - 1) / numThreads;

        for (int i = 0; i < numThreads; i++) {
            final int startY = 1 + (i * chunkSize);
            final int endY = Math.min(startY + chunkSize, height - 1);
            if (startY >= endY) break;

            Thread t = new Thread(() -> processRowRange(srcPixels, destPixels, width, startY, endY));
            threads.add(t);
            t.start();
        }

        for (Thread t : threads) t.join();

        if (formattedSrc != src) formattedSrc.flush();
        return dest;
    }

    public static void processRowRange(int[] srcPixels, int[] destPixels, int width, int startY, int endY) {
        for (int y = startY; y < endY; y++) {
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
                        int gray = (r * 77 + g * 150 + b * 29) >> 8; // Fast bitwise luminance

                        sumX += Gx[ky + 1][kx + 1] * gray;
                        sumY += Gy[ky + 1][kx + 1] * gray;
                    }
                }

                int magnitude = (int) Math.sqrt((sumX * sumX) + (sumY * sumY));
                magnitude = Math.min(255, Math.max(0, magnitude));
                destPixels[rowOffset + x] = (magnitude << 16) | (magnitude << 8) | magnitude;
            }
        }
    }

    private static BufferedImage ensureIntRgb(BufferedImage img) {
        if (img.getType() == BufferedImage.TYPE_INT_RGB) return img;
        BufferedImage rgb = new BufferedImage(img.getWidth(), img.getHeight(), BufferedImage.TYPE_INT_RGB);
        rgb.getGraphics().drawImage(img, 0, 0, null);
        return rgb;
    }
}

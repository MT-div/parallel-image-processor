package com.parallel.ui;

import com.parallel.filter.BatchProcessor;
import com.parallel.filter.BatchProcessor.ExecutionStrategy;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import java.awt.*;
import java.io.File;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * Modern Multi-Strategy Parallel Computing Dashboard.
 * Allows interactive evaluation of different architectural concurrency strategies.
 */
public class MainUI extends JFrame {

    private static final Color PRIMARY_COLOR = new Color(33, 150, 243);
    private static final Color SUCCESS_COLOR = new Color(46, 125, 50);
    private static final Color ACCENT_ORANGE = new Color(230, 81, 0);
    private static final Color BG_LIGHT = new Color(245, 247, 250);
    private static final Color CARD_BG = Color.WHITE;
    private static final Color TEXT_DARK = new Color(33, 33, 33);

    private JLabel folderLabel;
    private JLabel cpuInfoLabel;
    private JComboBox<String> threadSelector;
    private JComboBox<ExecutionStrategy> strategySelector;
    private JProgressBar progressBar;
    private JButton runButton;
    private JButton benchmarkButton;
    private JTextArea logArea;
    private ChartPanel chartPanel;

    private File selectedDirectory;
    private final int availableCores;

    public MainUI() {
        super("Parallel Image Processing Studio | Architectural Concurrency Benchmark");
        this.availableCores = Runtime.getRuntime().availableProcessors();
        initModernUI();
    }

    private void initModernUI() {
        setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        setSize(1220, 800);
        setLocationRelativeTo(null);
        getContentPane().setBackground(BG_LIGHT);
        setLayout(new BorderLayout(15, 15));

        // 1. Header & Controls
        JPanel headerCard = createStyledPanel();
        headerCard.setLayout(new BoxLayout(headerCard, BoxLayout.Y_AXIS));

        JPanel statusRow = new JPanel(new BorderLayout());
        statusRow.setOpaque(false);
        JLabel titleLabel = new JLabel("Parallel Image Edge Detection (Sobel Architecture Studio)");
        titleLabel.setFont(new Font("Segoe UI", Font.BOLD, 18));
        titleLabel.setForeground(TEXT_DARK);

        cpuInfoLabel = new JLabel(String.format("System: %d Logical CPU Cores Available", availableCores));
        cpuInfoLabel.setFont(new Font("Segoe UI", Font.ITALIC, 13));
        cpuInfoLabel.setForeground(new Color(100, 116, 139));

        statusRow.add(titleLabel, BorderLayout.WEST);
        statusRow.add(cpuInfoLabel, BorderLayout.EAST);
        headerCard.add(statusRow);
        headerCard.add(Box.createVerticalStrut(12));

        // Controls Row
        JPanel controlsRow = new JPanel(new FlowLayout(FlowLayout.LEFT, 12, 0));
        controlsRow.setOpaque(false);

        JButton browseBtn = createStyledButton("Choose Folder", new Color(74, 85, 104));
        folderLabel = new JLabel("No folder selected");
        folderLabel.setFont(new Font("Segoe UI", Font.PLAIN, 13));

        controlsRow.add(browseBtn);
        controlsRow.add(folderLabel);
        controlsRow.add(Box.createHorizontalStrut(10));

        controlsRow.add(new JLabel("Strategy:"));
        strategySelector = new JComboBox<>(ExecutionStrategy.values());
        controlsRow.add(strategySelector);

        controlsRow.add(new JLabel("Threads:"));
        List<String> threadOptions = new ArrayList<>();
        int[] availableThreads = {1, 2, 4, 8, 12, 16, 32, 64, 128};
        for (int t : availableThreads) {
            if (t == availableCores) {
                threadOptions.add(t + " (Recommended)");
            } else {
                threadOptions.add(String.valueOf(t));
            }
        }
        threadSelector = new JComboBox<>(threadOptions.toArray(new String[0]));
        for (int i = 0; i < threadOptions.size(); i++) {
            if (threadOptions.get(i).contains("Recommended")) {
                threadSelector.setSelectedIndex(i);
                break;
            }
        }
        controlsRow.add(threadSelector);

        runButton = createStyledButton("Run Comparison", PRIMARY_COLOR);
        runButton.setEnabled(false);
        benchmarkButton = createStyledButton("Benchmark Scales (1-128 Th)", new Color(106, 27, 154));
        benchmarkButton.setEnabled(false);

        controlsRow.add(runButton);
        controlsRow.add(benchmarkButton);
        headerCard.add(controlsRow);

        JPanel topContainer = new JPanel(new BorderLayout());
        topContainer.setOpaque(false);
        topContainer.setBorder(new EmptyBorder(12, 15, 0, 15));
        topContainer.add(headerCard, BorderLayout.CENTER);
        add(topContainer, BorderLayout.NORTH);

        // 2. Center Panel: Log & Multi-Bar Chart
        JPanel centerContainer = new JPanel(new GridLayout(1, 2, 15, 15));
        centerContainer.setOpaque(false);
        centerContainer.setBorder(new EmptyBorder(0, 15, 0, 15));

        JPanel logCard = createStyledPanel();
        logCard.setLayout(new BorderLayout(5, 5));
        JLabel logTitle = new JLabel("Execution Metrics & System Log");
        logTitle.setFont(new Font("Segoe UI", Font.BOLD, 14));
        logCard.add(logTitle, BorderLayout.NORTH);

        logArea = new JTextArea();
        logArea.setEditable(false);
        logArea.setFont(new Font("Consolas", Font.PLAIN, 13));
        logArea.setBackground(new Color(250, 250, 250));
        JScrollPane scrollLog = new JScrollPane(logArea);
        scrollLog.setBorder(BorderFactory.createLineBorder(new Color(226, 232, 240)));
        logCard.add(scrollLog, BorderLayout.CENTER);

        chartPanel = new ChartPanel();

        centerContainer.add(logCard);
        centerContainer.add(chartPanel);
        add(centerContainer, BorderLayout.CENTER);

        // 3. Bottom Progress Bar
        JPanel bottomCard = createStyledPanel();
        bottomCard.setLayout(new BorderLayout(8, 8));

        progressBar = new JProgressBar(0, 100);
        progressBar.setStringPainted(true);
        progressBar.setFont(new Font("Segoe UI", Font.BOLD, 12));
        progressBar.setForeground(PRIMARY_COLOR);
        progressBar.setPreferredSize(new Dimension(100, 24));

        bottomCard.add(progressBar, BorderLayout.CENTER);

        JPanel bottomContainer = new JPanel(new BorderLayout());
        bottomContainer.setOpaque(false);
        bottomContainer.setBorder(new EmptyBorder(0, 15, 15, 15));
        bottomContainer.add(bottomCard, BorderLayout.CENTER);
        add(bottomContainer, BorderLayout.SOUTH);

        // Listeners
        browseBtn.addActionListener(e -> chooseDirectory());
        runButton.addActionListener(e -> executeComparison());
        benchmarkButton.addActionListener(e -> executeFullBenchmark());
    }

    private JPanel createStyledPanel() {
        JPanel p = new JPanel();
        p.setBackground(CARD_BG);
        p.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(new Color(226, 232, 240), 1),
                new EmptyBorder(12, 15, 12, 15)
        ));
        return p;
    }

    private JButton createStyledButton(String text, Color bg) {
        JButton btn = new JButton(text);
        btn.setFont(new Font("Segoe UI", Font.BOLD, 13));
        btn.setForeground(Color.WHITE);
        btn.setBackground(bg);
        btn.setFocusPainted(false);
        btn.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(bg.darker(), 1),
                new EmptyBorder(6, 14, 6, 14)
        ));
        return btn;
    }

    private void chooseDirectory() {
        JFileChooser chooser = new JFileChooser();
        chooser.setFileSelectionMode(JFileChooser.DIRECTORIES_ONLY);
        if (chooser.showOpenDialog(this) == JFileChooser.APPROVE_OPTION) {
            selectedDirectory = chooser.getSelectedFile();
            List<File> images = getImageFiles();
            folderLabel.setText(selectedDirectory.getName() + " (" + images.size() + " images found)");
            boolean hasImages = !images.isEmpty();
            runButton.setEnabled(hasImages);
            benchmarkButton.setEnabled(hasImages);
            logArea.append("Folder Selected: " + selectedDirectory.getAbsolutePath() + " [" + images.size() + " images]\n");
        }
    }

    private List<File> getImageFiles() {
        if (selectedDirectory == null) return new ArrayList<>();
        File[] files = selectedDirectory.listFiles((dir, name) -> {
            String lower = name.toLowerCase();
            return lower.endsWith(".jpg") || lower.endsWith(".jpeg") || lower.endsWith(".png");
        });
        return files != null ? Arrays.asList(files) : new ArrayList<>();
    }

    private int getSelectedThreads() {
        String selected = (String) threadSelector.getSelectedItem();
        if (selected == null) return 1;
        return Integer.parseInt(selected.split(" ")[0]);
    }

    private void executeComparison() {
        List<File> images = getImageFiles();
        int threads = getSelectedThreads();
        ExecutionStrategy strategy = (ExecutionStrategy) strategySelector.getSelectedItem();

        setControlsEnabled(false);
        progressBar.setValue(0);
        logArea.append(String.format("\n--- Single Comparison (%d Images | %d Threads | %s) ---\n", images.size(), threads, strategy));

        File outSeq = new File(selectedDirectory, "output_seq");
        File outPar = new File(selectedDirectory, "output_par_" + strategy.name().toLowerCase());
        outSeq.mkdirs();
        outPar.mkdirs();

        new Thread(() -> {
            try {
                // Sequential
                long seqTime = BatchProcessor.processSequential(images, outSeq, (done, total) -> {
                    int p = (int) (((double) done / total) * 50);
                    SwingUtilities.invokeLater(() -> {
                        progressBar.setValue(p);
                        progressBar.setString("Sequential: " + done + " / " + total);
                    });
                });
                SwingUtilities.invokeLater(() -> logArea.append("Sequential Baseline: " + seqTime + " ms\n"));

                // Parallel with selected strategy
                SwingUtilities.invokeLater(() -> progressBar.setString("Parallel (" + threads + " Th): " + strategy + "..."));
                long parTime = BatchProcessor.processParallel(images, outPar, threads, strategy, (done, total) -> {
                    int p = 50 + (int) (((double) done / total) * 50);
                    SwingUtilities.invokeLater(() -> {
                        progressBar.setValue(p);
                        progressBar.setString("Parallel [" + strategy.name() + "]: " + done + " / " + total);
                    });
                });
                SwingUtilities.invokeLater(() -> logArea.append("Parallel (" + strategy + "): " + parTime + " ms\n"));

                double speedup = (double) seqTime / (parTime > 0 ? parTime : 1);
                SwingUtilities.invokeLater(() -> {
                    logArea.append(String.format("=> Achieved Speedup: %.2fx | Efficiency: %.2f\n", speedup, speedup / threads));
                    chartPanel.setSingleComparison(seqTime, parTime, speedup, threads, strategy.toString());
                    progressBar.setValue(100);
                    progressBar.setString("Done! Speedup: " + String.format("%.2fx", speedup));
                    setControlsEnabled(true);
                });
            } catch (Exception ex) {
                SwingUtilities.invokeLater(() -> {
                    logArea.append("Error: " + ex.getMessage() + "\n");
                    setControlsEnabled(true);
                });
            }
        }).start();
    }

    private void executeFullBenchmark() {
        List<File> images = getImageFiles();
        ExecutionStrategy strategy = (ExecutionStrategy) strategySelector.getSelectedItem();

        setControlsEnabled(false);
        progressBar.setValue(0);
        logArea.append(String.format("\n================ BENCHMARK SUITE: %s (%d Images) ================\n", strategy, images.size()));

        File outDir = new File(selectedDirectory, "output_benchmark");
        outDir.mkdirs();

        new Thread(() -> {
            try {
                SwingUtilities.invokeLater(() -> progressBar.setString("Measuring Baseline Sequential..."));
                long seqTime = BatchProcessor.processSequential(images, outDir, null);
                SwingUtilities.invokeLater(() -> {
                    logArea.append("Baseline Sequential Time: " + seqTime + " ms\n");
                    logArea.append("-------------------------------------------------------------------\n");
                    logArea.append(String.format("%-10s | %-12s | %-10s | %-10s\n", "Threads", "Time (ms)", "Speedup", "Efficiency"));
                    logArea.append("-------------------------------------------------------------------\n");
                });

                int[] testScales = {1, 2, 4, 8, 12, 16, 32, 64, 128};
                List<Integer> threadList = new ArrayList<>();
                List<Long> timeList = new ArrayList<>();
                List<Double> speedupList = new ArrayList<>();

                for (int idx = 0; idx < testScales.length; idx++) {
                    int t = testScales[idx];
                    final int stepIndex = idx + 1;
                    final int totalSteps = testScales.length;

                    long parTime = BatchProcessor.processParallel(images, outDir, t, strategy, (done, total) -> {
                        int overallPercent = (int) (((stepIndex - 1 + ((double) done / total)) / totalSteps) * 100);
                        SwingUtilities.invokeLater(() -> {
                            progressBar.setValue(overallPercent);
                            progressBar.setString("Benchmark: " + t + " Threads [" + done + "/" + total + "]");
                        });
                    });

                    double speedup = (double) seqTime / (parTime > 0 ? parTime : 1);
                    double eff = speedup / t;

                    threadList.add(t);
                    timeList.add(parTime);
                    speedupList.add(speedup);

                    SwingUtilities.invokeLater(() -> {
                        logArea.append(String.format("%-10d | %-12d | %-8.2fx | %-10.2f\n", t, parTime, speedup, eff));
                        chartPanel.setBenchmarkBars(seqTime, new ArrayList<>(threadList), new ArrayList<>(timeList), new ArrayList<>(speedupList), strategy.toString());
                    });
                }

                SwingUtilities.invokeLater(() -> {
                    logArea.append("================ BENCHMARK FINISHED ================\n");
                    progressBar.setValue(100);
                    progressBar.setString("Benchmark Complete!");
                    setControlsEnabled(true);
                });

            } catch (Exception ex) {
                SwingUtilities.invokeLater(() -> {
                    logArea.append("Benchmark Error: " + ex.getMessage() + "\n");
                    setControlsEnabled(true);
                });
            }
        }).start();
    }

    private void setControlsEnabled(boolean enabled) {
        runButton.setEnabled(enabled);
        benchmarkButton.setEnabled(enabled);
        threadSelector.setEnabled(enabled);
        strategySelector.setEnabled(enabled);
    }

    public static void main(String[] args) {
        SwingUtilities.invokeLater(() -> {
            MainUI ui = new MainUI();
            ui.setVisible(true);
        });
    }

    /**
     * Interactive Multi-Bar Chart displaying Speedup and Execution Time tags above bars.
     */
    private static class ChartPanel extends JPanel {
        private boolean isBenchmark = false;
        private String strategyName = "";

        private long seqTime = 0;
        private long parTime = 0;
        private double singleSpeedup = 0.0;
        private int singleThreads = 0;

        private List<Integer> benchThreads = new ArrayList<>();
        private List<Long> benchTimes = new ArrayList<>();
        private List<Double> benchSpeedups = new ArrayList<>();

        public ChartPanel() {
            setBackground(CARD_BG);
            setBorder(BorderFactory.createCompoundBorder(
                    BorderFactory.createLineBorder(new Color(226, 232, 240), 1),
                    new EmptyBorder(10, 15, 10, 15)
            ));
        }

        public void setSingleComparison(long seqTime, long parTime, double speedup, int threads, String strategy) {
            this.isBenchmark = false;
            this.seqTime = seqTime;
            this.parTime = parTime;
            this.singleSpeedup = speedup;
            this.singleThreads = threads;
            this.strategyName = strategy;
            repaint();
        }

        public void setBenchmarkBars(long seqTime, List<Integer> threads, List<Long> times, List<Double> speedups, String strategy) {
            this.isBenchmark = true;
            this.seqTime = seqTime;
            this.benchThreads = threads;
            this.benchTimes = times;
            this.benchSpeedups = speedups;
            this.strategyName = strategy;
            repaint();
        }

        @Override
        protected void paintComponent(Graphics g) {
            super.paintComponent(g);
            Graphics2D g2 = (Graphics2D) g;
            g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);

            int width = getWidth();
            int height = getHeight();

            if (!isBenchmark) {
                drawSingleBarChart(g2, width, height);
            } else {
                drawBenchmarkBarChart(g2, width, height);
            }
        }

        private void drawSingleBarChart(Graphics2D g2, int width, int height) {
            if (seqTime == 0 && parTime == 0) {
                g2.setColor(new Color(148, 163, 184));
                g2.setFont(new Font("Segoe UI", Font.PLAIN, 14));
                g2.drawString("Execution charts will render here after execution.", 30, height / 2);
                return;
            }

            int chartBaseY = height - 70;
            int maxBarHeight = height - 170;
            long maxTime = Math.max(seqTime, parTime);
            if (maxTime == 0) maxTime = 1;

            int barWidth = 90;
            int bar1X = width / 4 - barWidth / 2;
            int bar2X = (3 * width) / 4 - barWidth / 2;

            int bar1Height = (int) (((double) seqTime / maxTime) * maxBarHeight);
            int bar2Height = (int) (((double) parTime / maxTime) * maxBarHeight);

            // Sequential Bar
            g2.setColor(ACCENT_ORANGE);
            g2.fillRoundRect(bar1X, chartBaseY - bar1Height, barWidth, bar1Height, 8, 8);
            g2.setColor(TEXT_DARK);
            g2.setFont(new Font("Segoe UI", Font.BOLD, 13));
            g2.drawString("Sequential", bar1X + 12, chartBaseY + 22);
            g2.drawString(seqTime + " ms", bar1X + 18, chartBaseY - bar1Height - 8);

            // Parallel Bar
            g2.setColor(SUCCESS_COLOR);
            g2.fillRoundRect(bar2X, chartBaseY - bar2Height, barWidth, bar2Height, 8, 8);
            g2.setColor(TEXT_DARK);
            g2.drawString(singleThreads + " Th (" + strategyName.split(" ")[0] + ")", bar2X - 10, chartBaseY + 22);
            g2.drawString(parTime + " ms", bar2X + 18, chartBaseY - bar2Height - 26);

            // Speedup badge above parallel bar
            g2.setColor(PRIMARY_COLOR.darker());
            g2.setFont(new Font("Segoe UI", Font.BOLD, 15));
            g2.drawString(String.format("Speedup: %.2fx", singleSpeedup), bar2X - 5, chartBaseY - bar2Height - 8);
        }

        private void drawBenchmarkBarChart(Graphics2D g2, int width, int height) {
            if (benchThreads.isEmpty()) return;

            g2.setColor(TEXT_DARK);
            g2.setFont(new Font("Segoe UI", Font.BOLD, 14));
            g2.drawString("Strategy: " + strategyName + " (1 to 128 Threads)", 20, 25);

            int count = benchThreads.size();
            int chartBaseY = height - 60;
            int maxBarHeight = height - 170;

            long maxTime = seqTime;
            for (long t : benchTimes) {
                if (t > maxTime) maxTime = t;
            }
            if (maxTime == 0) maxTime = 1;

            double bestSpeedup = 0.0;
            for (double s : benchSpeedups) {
                if (s > bestSpeedup) bestSpeedup = s;
            }

            int availableWidth = width - 40;
            int barWidth = Math.min(50, (availableWidth / count) - 10);
            int gap = (availableWidth - (count * barWidth)) / (count + 1);

            for (int i = 0; i < count; i++) {
                int x = 20 + gap + i * (barWidth + gap);
                long time = benchTimes.get(i);
                double speedup = benchSpeedups.get(i);
                int barHeight = (int) (((double) time / maxTime) * maxBarHeight);

                if (speedup == bestSpeedup && speedup > 1.0) {
                    g2.setColor(SUCCESS_COLOR);
                } else {
                    g2.setColor(PRIMARY_COLOR);
                }

                g2.fillRoundRect(x, chartBaseY - barHeight, barWidth, barHeight, 6, 6);

                // Speedup text above bar
                g2.setColor(TEXT_DARK);
                g2.setFont(new Font("Segoe UI", Font.BOLD, 11));
                g2.drawString(String.format("%.1fx", speedup), x + 2, chartBaseY - barHeight - 8);

                // Threads & Time below bar
                g2.setColor(new Color(100, 116, 139));
                g2.drawString(benchThreads.get(i) + "T", x + (barWidth / 4), chartBaseY + 18);
                g2.setFont(new Font("Segoe UI", Font.PLAIN, 10));
                g2.drawString(time + "ms", x - 2, chartBaseY + 34);
            }
        }
    }
}

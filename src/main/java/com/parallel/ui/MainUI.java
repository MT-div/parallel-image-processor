package com.parallel.ui;

import com.parallel.filter.BatchProcessor;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import java.awt.*;
import java.io.File;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * Interactive Graphical User Interface (GUI) for the Parallel Image Processing Studio.
 * Ensures a responsive UI thread by dispatching heavy computations to background worker threads.
 */
public class MainUI extends JFrame {

    private JLabel folderLabel;
    private JComboBox<String> threadSelector;
    private JProgressBar progressBar;
    private JButton runButton;
    private JButton benchmarkButton;
    private JTextArea logArea;
    private ChartPanel chartPanel;

    private File selectedDirectory;
    private int recommendedThreads;

    public MainUI() {
        super("Parallel Image Processing Studio - Sobel Filter");
        initUI();
    }

    private void initUI() {
        setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        setSize(1000, 720);
        setLocationRelativeTo(null);
        setLayout(new BorderLayout(10, 10));

        recommendedThreads = Runtime.getRuntime().availableProcessors();

        // 1. Top Control Panel
        JPanel topPanel = new JPanel();
        topPanel.setLayout(new BoxLayout(topPanel, BoxLayout.Y_AXIS));
        topPanel.setBorder(new EmptyBorder(10, 15, 10, 15));

        JPanel dirRow = new JPanel(new FlowLayout(FlowLayout.LEFT));
        JButton browseBtn = new JButton("Select Images Folder");
        folderLabel = new JLabel("No folder selected");
        dirRow.add(browseBtn);
        dirRow.add(folderLabel);

        JPanel configRow = new JPanel(new FlowLayout(FlowLayout.LEFT));
        configRow.add(new JLabel("Worker Threads (N): "));

        List<String> threadOptions = new ArrayList<>();
        int[] availableThreads = {1, 2, 4, 8, 12, 16, 32, 64};
        for (int t : availableThreads) {
            if (t == recommendedThreads) {
                threadOptions.add(t + " (Recommended)");
            } else {
                threadOptions.add(String.valueOf(t));
            }
        }
        threadSelector = new JComboBox<>(threadOptions.toArray(new String[0]));
        // Select recommended by default
        for (int i = 0; i < threadOptions.size(); i++) {
            if (threadOptions.get(i).contains("Recommended")) {
                threadSelector.setSelectedIndex(i);
                break;
            }
        }
        configRow.add(threadSelector);

        runButton = new JButton("Run Execution & Compare");
        runButton.setEnabled(false);
        benchmarkButton = new JButton("Run Full Benchmark (1-64 Threads)");
        benchmarkButton.setEnabled(false);

        configRow.add(Box.createHorizontalStrut(15));
        configRow.add(runButton);
        configRow.add(benchmarkButton);

        topPanel.add(dirRow);
        topPanel.add(configRow);
        add(topPanel, BorderLayout.NORTH);

        // 2. Center Panel: Log area and Performance Chart
        JPanel centerPanel = new JPanel(new GridLayout(1, 2, 10, 10));
        centerPanel.setBorder(new EmptyBorder(0, 15, 0, 15));

        logArea = new JTextArea();
        logArea.setEditable(false);
        logArea.setFont(new Font("Consolas", Font.PLAIN, 13));
        JScrollPane scrollLog = new JScrollPane(logArea);

        chartPanel = new ChartPanel();
        centerPanel.add(scrollLog);
        centerPanel.add(chartPanel);
        add(centerPanel, BorderLayout.CENTER);

        // 3. Bottom Progress Bar
        JPanel bottomPanel = new JPanel(new BorderLayout(5, 5));
        bottomPanel.setBorder(new EmptyBorder(5, 15, 10, 15));
        progressBar = new JProgressBar(0, 100);
        progressBar.setStringPainted(true);
        bottomPanel.add(progressBar, BorderLayout.CENTER);
        add(bottomPanel, BorderLayout.SOUTH);

        // Event Listeners
        browseBtn.addActionListener(e -> chooseDirectory());
        runButton.addActionListener(e -> executeComparison());
        benchmarkButton.addActionListener(e -> executeFullBenchmark());
    }

    private void chooseDirectory() {
        JFileChooser chooser = new JFileChooser();
        chooser.setFileSelectionMode(JFileChooser.DIRECTORIES_ONLY);
        if (chooser.showOpenDialog(this) == JFileChooser.APPROVE_OPTION) {
            selectedDirectory = chooser.getSelectedFile();
            folderLabel.setText(selectedDirectory.getAbsolutePath());
            runButton.setEnabled(true);
            benchmarkButton.setEnabled(true);
            logArea.append("Selected Directory: " + selectedDirectory.getAbsolutePath() + "\n");
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
        String num = selected.split(" ")[0];
        return Integer.parseInt(num);
    }

    /**
     * Executes Single Comparison (Sequential vs Parallel) on a background thread.
     */
    private void executeComparison() {
        List<File> images = getImageFiles();
        if (images.isEmpty()) {
            JOptionPane.showMessageDialog(this, "No valid image files (JPG/PNG) found in the selected folder!", "Warning", JOptionPane.WARNING_MESSAGE);
            return;
        }

        int threads = getSelectedThreads();
        setControlsEnabled(false);
        logArea.append("\n--- Starting Batch Comparison (" + images.size() + " images) with " + threads + " threads ---\n");

        File outSeq = new File(selectedDirectory, "output_seq");
        File outPar = new File(selectedDirectory, "output_par");
        outSeq.mkdirs();
        outPar.mkdirs();

        new Thread(() -> {
            try {
                // 1. Sequential Run
                SwingUtilities.invokeLater(() -> logArea.append("1. Running Sequential Processing...\n"));
                long seqTime = BatchProcessor.processSequential(images, outSeq, (done, total) -> {
                    int percent = (int) (((double) done / total) * 50);
                    SwingUtilities.invokeLater(() -> progressBar.setValue(percent));
                });
                SwingUtilities.invokeLater(() -> logArea.append("-> Sequential Time: " + seqTime + " ms\n"));

                // 2. Parallel Run
                SwingUtilities.invokeLater(() -> logArea.append("2. Running Parallel Processing (" + threads + " threads)...\n"));
                long parTime = BatchProcessor.processParallel(images, outPar, threads, (done, total) -> {
                    int percent = 50 + (int) (((double) done / total) * 50);
                    SwingUtilities.invokeLater(() -> progressBar.setValue(percent));
                });
                SwingUtilities.invokeLater(() -> logArea.append("-> Parallel Time: " + parTime + " ms\n"));

                double speedup = (double) seqTime / (parTime > 0 ? parTime : 1);
                SwingUtilities.invokeLater(() -> {
                    logArea.append(String.format("=> Achieved Speedup: %.2fx (Efficiency: %.2f)\n", speedup, speedup / threads));
                    chartPanel.updateChart(seqTime, parTime, speedup, threads);
                    progressBar.setValue(100);
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

    /**
     * Executes Full Benchmark across all thread counts in a background thread.
     */
    private void executeFullBenchmark() {
        List<File> images = getImageFiles();
        if (images.isEmpty()) {
            JOptionPane.showMessageDialog(this, "No valid images found!", "Warning", JOptionPane.WARNING_MESSAGE);
            return;
        }

        setControlsEnabled(false);
        logArea.append("\n================ FULL BENCHMARK SUITE ================\n");
        File outDir = new File(selectedDirectory, "output_benchmark");
        outDir.mkdirs();

        new Thread(() -> {
            try {
                long seqTime = BatchProcessor.processSequential(images, outDir, null);
                SwingUtilities.invokeLater(() -> logArea.append("Sequential Baseline Time: " + seqTime + " ms\n"));

                int[] testScales = {1, 2, 4, 8, 12, 16, 32, 64};
                for (int t : testScales) {
                    long parTime = BatchProcessor.processParallel(images, outDir, t, null);
                    double speedup = (double) seqTime / (parTime > 0 ? parTime : 1);
                    double eff = speedup / t;
                    SwingUtilities.invokeLater(() -> logArea.append(String.format("Threads: %-2d | Time: %-6d ms | Speedup: %-5.2fx | Eff: %.2f\n", t, parTime, speedup, eff)));
                }
                SwingUtilities.invokeLater(() -> {
                    logArea.append("================ BENCHMARK FINISHED ================\n");
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
    }

    public static void main(String[] args) {
        SwingUtilities.invokeLater(() -> {
            MainUI ui = new MainUI();
            ui.setVisible(true);
        });
    }

    /**
     * Inner Component rendering execution comparison charts.
     */
    private static class ChartPanel extends JPanel {
        private long seqTime = 0;
        private long parTime = 0;
        private double speedup = 0.0;
        private int threads = 0;

        public ChartPanel() {
            setBackground(Color.WHITE);
            setBorder(BorderFactory.createTitledBorder("Execution Time & Speedup Comparison"));
        }

        public void updateChart(long seqTime, long parTime, double speedup, int threads) {
            this.seqTime = seqTime;
            this.parTime = parTime;
            this.speedup = speedup;
            this.threads = threads;
            repaint();
        }

        @Override
        protected void paintComponent(Graphics g) {
            super.paintComponent(g);
            if (seqTime == 0 && parTime == 0) {
                g.setColor(Color.GRAY);
                g.drawString("No execution data available yet. Run a comparison to view the chart.", 40, getHeight() / 2);
                return;
            }

            Graphics2D g2 = (Graphics2D) g;
            g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);

            int width = getWidth();
            int height = getHeight();
            int chartBaseY = height - 80;
            int maxBarHeight = height - 160;

            long maxTime = Math.max(seqTime, parTime);
            if (maxTime == 0) maxTime = 1;

            int barWidth = 90;
            int bar1X = width / 4 - barWidth / 2;
            int bar2X = (3 * width) / 4 - barWidth / 2;

            int bar1Height = (int) (((double) seqTime / maxTime) * maxBarHeight);
            int bar2Height = (int) (((double) parTime / maxTime) * maxBarHeight);

            // Sequential Bar (Orange/Red)
            g2.setColor(new Color(230, 81, 0));
            g2.fillRect(bar1X, chartBaseY - bar1Height, barWidth, bar1Height);
            g2.setColor(Color.BLACK);
            g2.drawRect(bar1X, chartBaseY - bar1Height, barWidth, bar1Height);
            g2.drawString("Sequential (1 Th)", bar1X - 5, chartBaseY + 20);
            g2.drawString(seqTime + " ms", bar1X + 15, chartBaseY - bar1Height - 8);

            // Parallel Bar (Green)
            g2.setColor(new Color(46, 125, 50));
            g2.fillRect(bar2X, chartBaseY - bar2Height, barWidth, bar2Height);
            g2.setColor(Color.BLACK);
            g2.drawRect(bar2X, chartBaseY - bar2Height, barWidth, bar2Height);
            g2.drawString("Parallel (" + threads + " Th)", bar2X, chartBaseY + 20);
            g2.drawString(parTime + " ms", bar2X + 15, chartBaseY - bar2Height - 8);

            // Speedup Badge
            g2.setColor(new Color(25, 118, 210));
            g2.fillRoundRect(width / 2 - 80, 30, 160, 40, 10, 10);
            g2.setColor(Color.WHITE);
            g2.setFont(new Font("SansSerif", Font.BOLD, 15));
            g2.drawString(String.format("Speedup: %.2fx", speedup), width / 2 - 60, 55);
        }
    }
}

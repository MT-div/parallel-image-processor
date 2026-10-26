package com.parallel.ui;

import com.parallel.model.BenchmarkResult;
import com.parallel.strategy.*;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import java.awt.*;
import java.io.File;
import java.util.*;
import java.util.List;

public class MainUI extends JFrame {

    private static final Color DYNAMIC_COLOR = new Color(33, 150, 243);    // Blue
    private static final Color STATIC_COLOR  = new Color(245, 124, 0);     // Orange
    private static final Color PIPELINE_COLOR = new Color(46, 125, 50);    // Green
    private static final Color CARD_BG = Color.WHITE;
    private static final Color TEXT_DARK = new Color(33, 33, 33);

    private JLabel folderLabel;
    private JComboBox<String> threadSelector;
    private JComboBox<String> strategySelector;
    private JProgressBar progressBar;
    private JButton runSingleButton;
    private JButton runUltimateButton;
    private JTextArea logArea;
    private MultiChartPanel chartPanel;

    private File selectedDirectory;
    private final int availableCores;

    private final Map<String, BatchStrategy> strategyMap = new LinkedHashMap<>();

    public MainUI() {
        super("Parallel Image Processing Studio | Clean Architecture Dashboard");
        this.availableCores = Runtime.getRuntime().availableProcessors();

        strategyMap.put("Dynamic Atomic Queue", new DynamicQueueStrategy());
        strategyMap.put("Static Chunk Partitioning", new StaticPartitionStrategy());
        strategyMap.put("Pipelined (I/O Decoupled)", new PipelinedStrategy());

        initCleanUI();
    }

    private void initCleanUI() {
        setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        setSize(1240, 820);
        setLocationRelativeTo(null);
        getContentPane().setBackground(new Color(245, 247, 250));
        setLayout(new BorderLayout(15, 15));

        // 1. Header Card
        JPanel headerCard = createCardPanel();
        headerCard.setLayout(new BoxLayout(headerCard, BoxLayout.Y_AXIS));

        JPanel statusRow = new JPanel(new BorderLayout());
        statusRow.setOpaque(false);
        JLabel title = new JLabel("Parallel Image Edge Detection (Clean Architecture)");
        title.setFont(new Font("Segoe UI", Font.BOLD, 18));
        JLabel cpuInfo = new JLabel("Hardware: " + availableCores + " Logical CPU Cores Available");
        cpuInfo.setFont(new Font("Segoe UI", Font.ITALIC, 13));
        cpuInfo.setForeground(new Color(100, 116, 139));
        statusRow.add(title, BorderLayout.WEST);
        statusRow.add(cpuInfo, BorderLayout.EAST);
        headerCard.add(statusRow);
        headerCard.add(Box.createVerticalStrut(10));

        JPanel controls = new JPanel(new FlowLayout(FlowLayout.LEFT, 10, 0));
        controls.setOpaque(false);

        JButton browse = createButton("Select Images Folder", new Color(74, 85, 104));
        folderLabel = new JLabel("No folder selected");
        controls.add(browse);
        controls.add(folderLabel);
        controls.add(Box.createHorizontalStrut(15));

        controls.add(new JLabel("Strategy:"));
        strategySelector = new JComboBox<>(strategyMap.keySet().toArray(new String[0]));
        controls.add(strategySelector);

        controls.add(new JLabel("Threads:"));
        List<String> threadOpts = new ArrayList<>();
        int[] threadsArr = {1, 2, 4, 8, 12, 16, 32, 64, 128};
        for (int t : threadsArr) {
            threadOpts.add(t == availableCores ? t + " (Recommended)" : String.valueOf(t));
        }
        threadSelector = new JComboBox<>(threadOpts.toArray(new String[0]));
        for (int i = 0; i < threadOpts.size(); i++) {
            if (threadOpts.get(i).contains("Recommended")) threadSelector.setSelectedIndex(i);
        }
        controls.add(threadSelector);

        runSingleButton = createButton("Run Selected", DYNAMIC_COLOR);
        runSingleButton.setEnabled(false);
        runUltimateButton = createButton("Ultimate Benchmark (All 3 Strategies)", new Color(106, 27, 154));
        runUltimateButton.setEnabled(false);

        controls.add(runSingleButton);
        controls.add(runUltimateButton);
        headerCard.add(controls);

        JPanel topWrap = new JPanel(new BorderLayout());
        topWrap.setOpaque(false);
        topWrap.setBorder(new EmptyBorder(12, 15, 0, 15));
        topWrap.add(headerCard, BorderLayout.CENTER);
        add(topWrap, BorderLayout.NORTH);

        // 2. Center: Log & Grouped Chart
        JPanel center = new JPanel(new GridLayout(1, 2, 15, 15));
        center.setOpaque(false);
        center.setBorder(new EmptyBorder(0, 15, 0, 15));

        JPanel logCard = createCardPanel();
        logCard.setLayout(new BorderLayout(5, 5));
        logCard.add(new JLabel("Execution & Benchmark Logs"), BorderLayout.NORTH);
        logArea = new JTextArea();
        logArea.setEditable(false);
        logArea.setFont(new Font("Consolas", Font.PLAIN, 12));
        logArea.setBackground(new Color(250, 250, 250));
        logCard.add(new JScrollPane(logArea), BorderLayout.CENTER);

        chartPanel = new MultiChartPanel();
        center.add(logCard);
        center.add(chartPanel);
        add(center, BorderLayout.CENTER);

        // 3. Bottom Progress Bar
        JPanel bottom = createCardPanel();
        bottom.setLayout(new BorderLayout());
        progressBar = new JProgressBar(0, 100);
        progressBar.setStringPainted(true);
        progressBar.setFont(new Font("Segoe UI", Font.BOLD, 12));
        progressBar.setPreferredSize(new Dimension(100, 24));
        bottom.add(progressBar, BorderLayout.CENTER);

        JPanel bottomWrap = new JPanel(new BorderLayout());
        bottomWrap.setOpaque(false);
        bottomWrap.setBorder(new EmptyBorder(0, 15, 15, 15));
        bottomWrap.add(bottom, BorderLayout.CENTER);
        add(bottomWrap, BorderLayout.SOUTH);

        // Listeners
        browse.addActionListener(e -> chooseDirectory());
        runSingleButton.addActionListener(e -> executeSingle());
        runUltimateButton.addActionListener(e -> executeUltimateBenchmark());
    }

    private JPanel createCardPanel() {
        JPanel p = new JPanel();
        p.setBackground(CARD_BG);
        p.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(new Color(226, 232, 240), 1),
                new EmptyBorder(10, 15, 10, 15)
        ));
        return p;
    }

    private JButton createButton(String title, Color c) {
        JButton b = new JButton(title);
        b.setFont(new Font("Segoe UI", Font.BOLD, 12));
        b.setForeground(Color.WHITE);
        b.setBackground(c);
        b.setFocusPainted(false);
        b.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(c.darker(), 1),
                new EmptyBorder(6, 12, 6, 12)
        ));
        return b;
    }

    private void chooseDirectory() {
        JFileChooser ch = new JFileChooser();
        ch.setFileSelectionMode(JFileChooser.DIRECTORIES_ONLY);
        if (ch.showOpenDialog(this) == JFileChooser.APPROVE_OPTION) {
            selectedDirectory = ch.getSelectedFile();
            List<File> images = getImages();
            folderLabel.setText(selectedDirectory.getName() + " (" + images.size() + " images)");
            boolean ok = !images.isEmpty();
            runSingleButton.setEnabled(ok);
            runUltimateButton.setEnabled(ok);
            logArea.append("Folder Selected: " + selectedDirectory.getAbsolutePath() + " [" + images.size() + " images]\n");
        }
    }

    private List<File> getImages() {
        if (selectedDirectory == null) return new ArrayList<>();
        File[] arr = selectedDirectory.listFiles((d, n) -> {
            String l = n.toLowerCase();
            return l.endsWith(".jpg") || l.endsWith(".jpeg") || l.endsWith(".png");
        });
        return arr != null ? Arrays.asList(arr) : new ArrayList<>();
    }

    private int getSelectedThreads() {
        String s = (String) threadSelector.getSelectedItem();
        return s == null ? 1 : Integer.parseInt(s.split(" ")[0]);
    }

    private void executeSingle() {
        List<File> images = getImages();
        int threads = getSelectedThreads();
        String stratKey = (String) strategySelector.getSelectedItem();
        BatchStrategy strategy = strategyMap.get(stratKey);

        setControls(false);
        logArea.append(String.format("\n--- Running Single Comparison [%s | %d Th | %d Images] ---\n", stratKey, threads, images.size()));

        File outSeq = new File(selectedDirectory, "out_seq");
        File outPar = new File(selectedDirectory, "out_par_" + strategy.getClass().getSimpleName().toLowerCase());
        outSeq.mkdirs();
        outPar.mkdirs();

        new Thread(() -> {
            try {
                // Sequential
                SequentialStrategy seq = new SequentialStrategy();
                long seqTime = seq.execute(images, outSeq, 1, (d, t) -> {
                    int p = (int) (((double) d / t) * 50);
                    SwingUtilities.invokeLater(() -> {
                        progressBar.setValue(p);
                        progressBar.setString("Sequential: " + d + "/" + t);
                    });
                });
                SwingUtilities.invokeLater(() -> logArea.append("Sequential Baseline: " + seqTime + " ms\n"));

                // Selected Strategy
                long parTime = strategy.execute(images, outPar, threads, (d, t) -> {
                    int p = 50 + (int) (((double) d / t) * 50);
                    SwingUtilities.invokeLater(() -> {
                        progressBar.setValue(p);
                        progressBar.setString("Parallel [" + stratKey + "]: " + d + "/" + t);
                    });
                });
                SwingUtilities.invokeLater(() -> logArea.append(stratKey + " Time: " + parTime + " ms\n"));

                double speedup = (double) seqTime / (parTime > 0 ? parTime : 1);
                SwingUtilities.invokeLater(() -> {
                    logArea.append(String.format("=> Speedup: %.2fx | Efficiency: %.2f\n", speedup, speedup / threads));
                    chartPanel.setSingleResult(seqTime, parTime, speedup, threads, stratKey);
                    progressBar.setValue(100);
                    progressBar.setString("Done! Speedup: " + String.format("%.2fx", speedup));
                    setControls(true);
                });
            } catch (Exception ex) {
                SwingUtilities.invokeLater(() -> {
                    logArea.append("Error: " + ex.getMessage() + "\n");
                    setControls(true);
                });
            }
        }).start();
    }

    private void executeUltimateBenchmark() {
        List<File> images = getImages();
        setControls(false);
        logArea.append(String.format("\n================ ULTIMATE BENCHMARK: 3 STRATEGIES x 9 SCALES (%d Images) ================\n", images.size()));

        File outBench = new File(selectedDirectory, "out_ultimate_benchmark");
        outBench.mkdirs();

        new Thread(() -> {
            try {
                // Baseline
                SequentialStrategy seq = new SequentialStrategy();
                long seqTime = seq.execute(images, outBench, 1, null);
                SwingUtilities.invokeLater(() -> logArea.append("Sequential Baseline Time: " + seqTime + " ms\n\n"));

                int[] testScales = {1, 2, 4, 8, 12, 16, 32, 64, 128};
                int totalSteps = testScales.length * strategyMap.size();
                int currentStep = 0;

                List<BenchmarkResult> results = new ArrayList<>();

                for (int t : testScales) {
                    for (Map.Entry<String, BatchStrategy> entry : strategyMap.entrySet()) {
                        String sName = entry.getKey();
                        BatchStrategy strategy = entry.getValue();
                        currentStep++;
                        final int stepNum = currentStep;

                        long parTime = strategy.execute(images, outBench, t, (d, tot) -> {
                            int overall = (int) (((stepNum - 1 + ((double) d / tot)) / totalSteps) * 100);
                            SwingUtilities.invokeLater(() -> {
                                progressBar.setValue(overall);
                                progressBar.setString(sName + " (" + t + "T) [" + d + "/" + tot + "]");
                            });
                        });

                        double speedup = (double) seqTime / (parTime > 0 ? parTime : 1);
                        double eff = speedup / t;

                        BenchmarkResult res = new BenchmarkResult(sName, t, parTime, speedup, eff);
                        results.add(res);

                        SwingUtilities.invokeLater(() -> {
                            logArea.append(String.format("%-28s | %-3dT | Time: %-6d ms | Speedup: %-5.2fx | Eff: %.2f\n", sName, t, parTime, speedup, eff));
                            chartPanel.setUltimateResults(seqTime, new ArrayList<>(results));
                        });
                    }
                    SwingUtilities.invokeLater(() -> logArea.append("------------------------------------------------------------------------------------\n"));
                }

                SwingUtilities.invokeLater(() -> {
                    logArea.append("================ ULTIMATE BENCHMARK FINISHED ================\n");
                    progressBar.setValue(100);
                    progressBar.setString("All Benchmarks Complete!");
                    setControls(true);
                });

            } catch (Exception ex) {
                SwingUtilities.invokeLater(() -> {
                    logArea.append("Benchmark Error: " + ex.getMessage() + "\n");
                    setControls(true);
                });
            }
        }).start();
    }

    private void setControls(boolean enabled) {
        runSingleButton.setEnabled(enabled);
        runUltimateButton.setEnabled(enabled);
        threadSelector.setEnabled(enabled);
        strategySelector.setEnabled(enabled);
    }

    public static void main(String[] args) {
        SwingUtilities.invokeLater(() -> new MainUI().setVisible(true));
    }

    /**
     * Grouped Multi-Bar Chart displaying comparison of strategies side-by-side.
     */
    private static class MultiChartPanel extends JPanel {
        private boolean isUltimate = false;

        // Single data
        private long seqTime = 0, parTime = 0;
        private double singleSpeedup = 0.0;
        private int singleThreads = 0;
        private String singleStrategy = "";

        // Ultimate data
        private List<BenchmarkResult> ultimateResults = new ArrayList<>();

        public MultiChartPanel() {
            setBackground(CARD_BG);
            setBorder(BorderFactory.createCompoundBorder(
                    BorderFactory.createLineBorder(new Color(226, 232, 240), 1),
                    new EmptyBorder(10, 15, 10, 15)
            ));
        }

        public void setSingleResult(long seq, long par, double sp, int th, String strat) {
            this.isUltimate = false;
            this.seqTime = seq;
            this.parTime = par;
            this.singleSpeedup = sp;
            this.singleThreads = th;
            this.singleStrategy = strat;
            repaint();
        }

        public void setUltimateResults(long seq, List<BenchmarkResult> results) {
            this.isUltimate = true;
            this.seqTime = seq;
            this.ultimateResults = results;
            repaint();
        }

        @Override
        protected void paintComponent(Graphics g) {
            super.paintComponent(g);
            Graphics2D g2 = (Graphics2D) g;
            g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);

            int w = getWidth();
            int h = getHeight();

            if (!isUltimate) {
                drawSingle(g2, w, h);
            } else {
                drawUltimateGrouped(g2, w, h);
            }
        }

        private void drawSingle(Graphics2D g2, int w, int h) {
            if (seqTime == 0 && parTime == 0) {
                g2.setColor(new Color(148, 163, 184));
                g2.drawString("Execution charts will render here.", 30, h / 2);
                return;
            }

            int baseY = h - 60;
            int maxBarH = h - 160;
            long maxTime = Math.max(seqTime, parTime);
            if (maxTime == 0) maxTime = 1;

            int bw = 85;
            int b1X = w / 4 - bw / 2;
            int b2X = (3 * w) / 4 - bw / 2;

            int h1 = (int) (((double) seqTime / maxTime) * maxBarH);
            int h2 = (int) (((double) parTime / maxTime) * maxBarH);

            // Sequential
            g2.setColor(STATIC_COLOR);
            g2.fillRoundRect(b1X, baseY - h1, bw, h1, 8, 8);
            g2.setColor(TEXT_DARK);
            g2.drawString("Sequential", b1X + 12, baseY + 20);
            g2.drawString(seqTime + " ms", b1X + 18, baseY - h1 - 8);

            // Parallel
            g2.setColor(DYNAMIC_COLOR);
            g2.fillRoundRect(b2X, baseY - h2, bw, h2, 8, 8);
            g2.setColor(TEXT_DARK);
            g2.drawString(singleThreads + "T (" + singleStrategy.split(" ")[0] + ")", b2X - 5, baseY + 20);
            g2.drawString(parTime + " ms", b2X + 15, baseY - h2 - 25);

            g2.setColor(PIPELINE_COLOR);
            g2.setFont(new Font("Segoe UI", Font.BOLD, 14));
            g2.drawString(String.format("Speedup: %.2fx", singleSpeedup), b2X - 5, baseY - h2 - 8);
        }

        private void drawUltimateGrouped(Graphics2D g2, int w, int h) {
            if (ultimateResults.isEmpty()) return;

            // Draw Legend
            g2.setFont(new Font("Segoe UI", Font.BOLD, 11));
            g2.setColor(DYNAMIC_COLOR);
            g2.fillRect(w - 380, 15, 12, 12);
            g2.setColor(TEXT_DARK);
            g2.drawString("Dynamic Queue", w - 362, 25);

            g2.setColor(STATIC_COLOR);
            g2.fillRect(w - 260, 15, 12, 12);
            g2.setColor(TEXT_DARK);
            g2.drawString("Static Partition", w - 242, 25);

            g2.setColor(PIPELINE_COLOR);
            g2.fillRect(w - 140, 15, 12, 12);
            g2.setColor(TEXT_DARK);
            g2.drawString("Pipeline (I/O)", w - 122, 25);

            // Group by thread count
            Map<Integer, List<BenchmarkResult>> grouped = new LinkedHashMap<>();
            for (BenchmarkResult r : ultimateResults) {
                grouped.computeIfAbsent(r.getThreads(), k -> new ArrayList<>()).add(r);
            }

            int baseY = h - 50;
            int maxBarH = h - 140;

            // Find Max Speedup for scaling bar heights
            double maxSp = 1.0;
            for (BenchmarkResult r : ultimateResults) {
                if (r.getSpeedup() > maxSp) maxSp = r.getSpeedup();
            }
            if (maxSp == 0) maxSp = 1.0;

            int groupCount = grouped.size();
            int groupWidth = (w - 60) / Math.max(1, groupCount);
            int barWidth = Math.max(8, (groupWidth - 14) / 3);

            int gIdx = 0;
            for (Map.Entry<Integer, List<BenchmarkResult>> entry : grouped.entrySet()) {
                int threads = entry.getKey();
                List<BenchmarkResult> list = entry.getValue();
                int groupStartX = 30 + (gIdx * groupWidth);

                for (int i = 0; i < list.size(); i++) {
                    BenchmarkResult res = list.get(i);
                    int x = groupStartX + (i * barWidth);
                    int barH = (int) ((res.getSpeedup() / maxSp) * maxBarH);

                    if (res.getStrategyName().contains("Dynamic")) g2.setColor(DYNAMIC_COLOR);
                    else if (res.getStrategyName().contains("Static")) g2.setColor(STATIC_COLOR);
                    else g2.setColor(PIPELINE_COLOR);

                    g2.fillRoundRect(x, baseY - barH, barWidth - 2, barH, 4, 4);

                    // Speedup text over bar
                    g2.setColor(TEXT_DARK);
                    g2.setFont(new Font("Segoe UI", Font.PLAIN, 9));
                    g2.drawString(String.format("%.1f", res.getSpeedup()), x - 2, baseY - barH - 4);
                }

                // Group Label (Threads)
                g2.setColor(new Color(71, 85, 105));
                g2.setFont(new Font("Segoe UI", Font.BOLD, 11));
                g2.drawString(threads + "T", groupStartX + (groupWidth / 3) - 5, baseY + 18);
                gIdx++;
            }
        }
    }
}

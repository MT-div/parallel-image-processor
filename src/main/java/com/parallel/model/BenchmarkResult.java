package com.parallel.model;

public class BenchmarkResult {
    private final String strategyName;
    private final int threads;
    private final long executionTime;
    private final double speedup;
    private final double efficiency;

    public BenchmarkResult(String strategyName, int threads, long executionTime, double speedup, double efficiency) {
        this.strategyName = strategyName;
        this.threads = threads;
        this.executionTime = executionTime;
        this.speedup = speedup;
        this.efficiency = efficiency;
    }

    public String getStrategyName() { return strategyName; }
    public int getThreads() { return threads; }
    public long getExecutionTime() { return executionTime; }
    public double getSpeedup() { return speedup; }
    public double getEfficiency() { return efficiency; }
}

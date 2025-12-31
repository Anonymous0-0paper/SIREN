package org.siren.core;

import java.util.*;

/**
 * Represents the cloud datacenter in the fog-cloud topology.
 * Cloud has unlimited resources and negligible failure rate.
 */
public class CloudDataCenter {
    private String datacenterName;
    private long cpuCapacity;              // CPU capacity in MIPS (effectively unlimited)
    private long ramCapacity;              // RAM capacity in MB (effectively unlimited)
    private long bandwidthCapacity;        // Bandwidth capacity in Mbps
    private double failureRate;            // Failure rate (nearly 0)
    private List<SirenTask> processingTasks;
    private long totalEnergyConsumed;
    private Random random;
    
    /**
     * Constructor for CloudDataCenter
     */
    public CloudDataCenter(String name, long cpuCapacity, long ramCapacity, long bandwidthCapacity) {
        this.datacenterName = name;
        this.cpuCapacity = cpuCapacity;
        this.ramCapacity = ramCapacity;
        this.bandwidthCapacity = bandwidthCapacity;
        this.failureRate = 1e-8;  // Negligible failure rate
        this.processingTasks = new ArrayList<>();
        this.totalEnergyConsumed = 0;
        this.random = new Random(42);
    }
    
    /**
     * Check if cloud can accommodate task (always true, unlimited resources)
     */
    public boolean canAccommodate(SirenTask task) {
        return true;  // Cloud has unlimited resources
    }
    
    /**
     * Assign task to cloud
     */
    public void assignTask(SirenTask task) {
        processingTasks.add(task);
    }
    
    /**
     * Remove task from cloud
     */
    public void removeTask(SirenTask task) {
        processingTasks.remove(task);
    }
    
    /**
     * Get cloud success probability (nearly 1.0)
     * Cloud only fails with extremely low probability
     */
    public double getSuccessProbability(double executionTimeSec) {
        return Math.exp(-failureRate * executionTimeSec);  // ~1.0
    }
    
    /**
     * Check if cloud fails (virtually never)
     */
    public boolean checkFailure() {
        return random.nextDouble() < failureRate;
    }
    
    /**
     * Compute execution time on cloud
     * Cloud is fast with high CPU frequency (default 3.0 GHz)
     */
    public double getExecutionTime(SirenTask task) {
        double cloudMips = 10000;  // Cloud has very high MIPS
        double cloudFrequency = 3.0;  // Cloud runs at 3.0 GHz
        return task.getWorkloadMi() / (cloudMips * cloudFrequency);
    }
    
    /**
     * Compute energy consumption for task execution
     * Cloud typically more power-efficient per unit computation
     */
    public double computeExecutionEnergy(SirenTask task) {
        double executionTime = getExecutionTime(task);
        double powerWatts = 2.0;  // Cloud typical power consumption
        return powerWatts * executionTime;  // Energy in Joules
    }
    
    /**
     * Compute communication energy (task -> cloud)
     */
    public double computeCommunicationEnergy(double dataTransferMb, double transferTimeSec) {
        double txPower = 0.5;  // Transmission power in Watts
        double rxPower = 0.3;  // Reception power in Watts
        return (txPower + rxPower) * transferTimeSec;  // Energy in Joules
    }
    
    /**
     * Execute task on cloud and track energy
     */
    public double executeTask(SirenTask task) {
        double executionTime = getExecutionTime(task);
        double energy = computeExecutionEnergy(task);
        totalEnergyConsumed += energy;
        return executionTime;
    }
    
    /**
     * Get current number of processing tasks
     */
    public int getNumProcessingTasks() {
        return processingTasks.size();
    }
    
    /**
     * Get all processing tasks
     */
    public List<SirenTask> getProcessingTasks() {
        return new ArrayList<>(processingTasks);
    }
    
    /**
     * Get total energy consumed
     */
    public long getTotalEnergyConsumed() {
        return totalEnergyConsumed;
    }
    
    /**
     * Add energy consumption
     */
    public void addEnergyConsumption(double energy) {
        totalEnergyConsumed += energy;
    }
    
    /**
     * Get datacenter name
     */
    public String getDatacenterName() {
        return datacenterName;
    }
    
    /**
     * Get CPU capacity
     */
    public long getCpuCapacity() {
        return cpuCapacity;
    }
    
    /**
     * Get RAM capacity
     */
    public long getRamCapacity() {
        return ramCapacity;
    }
    
    /**
     * Get bandwidth capacity
     */
    public long getBandwidthCapacity() {
        return bandwidthCapacity;
    }
    
    /**
     * Get failure rate
     */
    public double getFailureRate() {
        return failureRate;
    }
    
    /**
     * Reset cloud state for next simulation
     */
    public void reset() {
        processingTasks.clear();
        totalEnergyConsumed = 0;
    }
    
    @Override
    public String toString() {
        return "CloudDataCenter{" +
                "name='" + datacenterName + '\'' +
                ", cpuCapacity=" + cpuCapacity +
                ", ramCapacity=" + ramCapacity +
                ", bandwidthCapacity=" + bandwidthCapacity +
                ", processingTasks=" + processingTasks.size() +
                ", totalEnergy=" + totalEnergyConsumed +
                ", failureRate=" + failureRate +
                '}';
    }
}

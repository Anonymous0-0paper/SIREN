package org.siren.core;

import java.util.*;

/**
 * Represents a task in the SIREN fog-cloud computing environment.
 * Encapsulates task attributes and execution state.
 */
public class SirenTask {
    private int taskId;
    private String taskName;
    private double workloadMi;              // Task workload in Million Instructions
    private double inputDataMb;             // Input data size in MB
    private double outputDataMb;            // Output data size in MB
    private double memoryMb;                // Memory requirement in MB
    private double deadlineSeconds;         // Task deadline in seconds
    private boolean isCritical;             // Whether task is mission-critical
    private int replicationFactor;          // Number of replicas (1-3)
    private List<Integer> assignedNodeIds;  // List of assigned fog node IDs
    private long creationTime;              // Task creation timestamp
    private long completionTime;            // Task completion timestamp
    private boolean completed;              // Whether task completed successfully
    private boolean failed;                 // Whether task failed
    private double totalExecutionTime;      // Total execution time in seconds
    private double totalEnergy;             // Total energy consumed in Joules
    private Random random;
    
    /**
     * Constructor for SirenTask
     */
    public SirenTask(int taskId, String taskName, double workloadMi, 
                     double inputDataMb, double outputDataMb, 
                     double memoryMb, double deadlineSeconds, boolean isCritical) {
        this.taskId = taskId;
        this.taskName = taskName;
        this.workloadMi = workloadMi;
        this.inputDataMb = inputDataMb;
        this.outputDataMb = outputDataMb;
        this.memoryMb = memoryMb;
        this.deadlineSeconds = deadlineSeconds;
        this.isCritical = isCritical;
        this.replicationFactor = isCritical ? 3 : 1;  // Critical tasks replicated 3x
        this.assignedNodeIds = new ArrayList<>();
        this.creationTime = System.currentTimeMillis();
        this.completed = false;
        this.failed = false;
        this.totalExecutionTime = 0.0;
        this.totalEnergy = 0.0;
        this.random = new Random(taskId * 42);  // Deterministic seed per task
    }
    
    /**
     * Assign this task to a fog node
     */
    public void assignToNode(int nodeId) {
        if (!assignedNodeIds.contains(nodeId)) {
            assignedNodeIds.add(nodeId);
        }
    }
    
    /**
     * Unassign this task from a fog node
     */
    public void unassignFromNode(int nodeId) {
        assignedNodeIds.remove(Integer.valueOf(nodeId));
    }
    
    /**
     * Check if task deadline is met
     */
    public boolean isDeadlineMet(double elapsedTimeSeconds) {
        return elapsedTimeSeconds <= deadlineSeconds;
    }
    
    /**
     * Check if task failed (with replication)
     * For k replicas, task succeeds if at least 1 succeeds
     * Task fails if all replicas fail
     */
    public boolean checkTaskFailure(double nodeSuccessProb) {
        // Probability that all replicas fail = (1 - p)^k
        double failureProbability = Math.pow(1.0 - nodeSuccessProb, replicationFactor);
        return random.nextDouble() < failureProbability;
    }
    
    /**
     * Calculate task success probability with replication
     * P_task_success = 1 - (1 - p_node)^k
     */
    public double getSuccessProbability(double nodeSuccessProb) {
        return 1.0 - Math.pow(1.0 - nodeSuccessProb, replicationFactor);
    }
    
    /**
     * Mark task as completed successfully
     */
    public void markCompleted() {
        this.completed = true;
        this.failed = false;
        this.completionTime = System.currentTimeMillis();
    }
    
    /**
     * Mark task as failed
     */
    public void markFailed() {
        this.completed = false;
        this.failed = true;
        this.completionTime = System.currentTimeMillis();
    }
    
    /**
     * Get total data transfer size (input + output)
     */
    public double getTotalDataSize() {
        return inputDataMb + outputDataMb;
    }
    
    /**
     * Get data transfer time given bandwidth
     * Time = Data / Bandwidth
     */
    public double getTransferTime(int bandwidthMbps) {
        return (getTotalDataSize() * 8.0) / bandwidthMbps;  // Convert MB to Mbps
    }
    
    /**
     * Get execution time on given node
     * Time = Workload / (MIPS * Frequency)
     */
    public double getExecutionTime(int nodeMips, double frequencyGHz) {
        return workloadMi / (nodeMips * frequencyGHz);
    }
    
    /**
     * Check if task meets all constraints
     */
    public boolean isConstraintSatisfied(int nodeMips, int nodeRam, int nodeBandwidth) {
        boolean cpuOk = workloadMi <= nodeMips;
        boolean memOk = memoryMb <= nodeRam;
        boolean bwOk = nodeBandwidth > 0;
        return cpuOk && memOk && bwOk;
    }
    
    // Getters and Setters
    
    public int getTaskId() {
        return taskId;
    }
    
    public String getTaskName() {
        return taskName;
    }
    
    public double getWorkloadMi() {
        return workloadMi;
    }
    
    public double getInputDataMb() {
        return inputDataMb;
    }
    
    public double getOutputDataMb() {
        return outputDataMb;
    }
    
    public double getMemoryMb() {
        return memoryMb;
    }
    
    public double getDeadlineSeconds() {
        return deadlineSeconds;
    }
    
    public boolean isCritical() {
        return isCritical;
    }
    
    public int getReplicationFactor() {
        return replicationFactor;
    }
    
    public void setReplicationFactor(int factor) {
        this.replicationFactor = Math.max(1, Math.min(3, factor));
    }
    
    public List<Integer> getAssignedNodeIds() {
        return new ArrayList<>(assignedNodeIds);
    }
    
    public boolean isCompleted() {
        return completed;
    }
    
    public boolean isFailed() {
        return failed;
    }
    
    public long getCreationTime() {
        return creationTime;
    }
    
    public long getCompletionTime() {
        return completionTime;
    }
    
    public double getTotalExecutionTime() {
        return totalExecutionTime;
    }
    
    public void setTotalExecutionTime(double time) {
        this.totalExecutionTime = time;
    }
    
    public double getTotalEnergy() {
        return totalEnergy;
    }
    
    public void addEnergy(double energy) {
        this.totalEnergy += energy;
    }
    
    public void setTotalEnergy(double energy) {
        this.totalEnergy = energy;
    }
    
    /**
     * Get response time (time from creation to completion)
     */
    public double getResponseTime() {
        if (!completed && !failed) {
            return -1.0;  // Task still running
        }
        return (completionTime - creationTime) / 1000.0;  // Convert to seconds
    }
    
    @Override
    public String toString() {
        return "SirenTask{" +
                "taskId=" + taskId +
                ", taskName='" + taskName + '\'' +
                ", workloadMi=" + workloadMi +
                ", inputData=" + inputDataMb +
                ", outputData=" + outputDataMb +
                ", memory=" + memoryMb +
                ", deadline=" + deadlineSeconds +
                ", critical=" + isCritical +
                ", replicas=" + replicationFactor +
                ", assignedNodes=" + assignedNodeIds.size() +
                ", completed=" + completed +
                ", failed=" + failed +
                ", executionTime=" + totalExecutionTime +
                ", energy=" + totalEnergy +
                '}';
    }
}

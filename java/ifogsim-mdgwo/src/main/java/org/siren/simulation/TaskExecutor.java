package org.siren.simulation;

import org.siren.core.*;
import java.util.*;
import java.util.stream.Collectors;

/**
 * Executes tasks on the fog-cloud topology and tracks execution metrics.
 * Implements probabilistic failure modeling and energy computation.
 */
public class TaskExecutor {
    private FogCloudTopology topology;
    private List<SirenTask> allTasks;
    private List<SirenTask> completedTasks;
    private List<SirenTask> failedTasks;
    private double currentSimulationTime;
    private Random random;
    
    /**
     * Constructor for TaskExecutor
     */
    public TaskExecutor(FogCloudTopology topology) {
        this.topology = topology;
        this.allTasks = new ArrayList<>();
        this.completedTasks = new ArrayList<>();
        this.failedTasks = new ArrayList<>();
        this.currentSimulationTime = 0.0;
        this.random = new Random(42);
    }
    
    /**
     * Add task to execution queue
     */
    public void addTask(SirenTask task) {
        allTasks.add(task);
    }
    
    /**
     * Add multiple tasks
     */
    public void addTasks(List<SirenTask> tasks) {
        allTasks.addAll(tasks);
    }
    
    /**
     * Execute a task with possible replication on multiple nodes
     */
    public ExecutionResult executeTask(SirenTask task) {
        ExecutionResult result = new ExecutionResult(task.getTaskId());
        
        List<Integer> assignedNodes = task.getAssignedNodeIds();
        
        // If task is not assigned to any node, offload to cloud
        if (assignedNodes.isEmpty()) {
            return executeTaskOnCloud(task);
        }
        
        // Execute task on assigned nodes (for replication)
        double totalEnergy = 0.0;
        double executionTime = 0.0;
        boolean taskSucceeded = false;
        int successCount = 0;
        int failureCount = 0;
        
        for (int nodeId : assignedNodes) {
            FogNode node = topology.getFogNode(nodeId);
            if (node == null) {
                failureCount++;
                continue;
            }
            
            // Get node success probability
            double nodeExecutionTime = task.getExecutionTime(node.getMips(), node.getCurrentFrequency());
            double nodeSuccessProbability = node.getSuccessProbability(nodeExecutionTime);
            
            // Check if this replica succeeds
            boolean replicaSucceeds = random.nextDouble() < nodeSuccessProbability;
            
            if (replicaSucceeds) {
                successCount++;
                taskSucceeded = true;
                
                // Compute energy for this execution
                double computeEnergy = node.computeComputationEnergy(task.getWorkloadMi(), nodeExecutionTime);
                double commEnergy = node.computeCommunicationEnergy(task.getTotalDataSize(), nodeExecutionTime);
                totalEnergy += computeEnergy + commEnergy;
                
                // Update execution time (take minimum)
                if (executionTime == 0.0 || nodeExecutionTime < executionTime) {
                    executionTime = nodeExecutionTime;
                }
                
                // Update node energy
                node.assignTask(task);
                node.executeTask(task);
            } else {
                failureCount++;
            }
        }
        
        // Task succeeds if at least one replica succeeds
        if (taskSucceeded && task.isDeadlineMet(executionTime)) {
            task.markCompleted();
            task.setTotalExecutionTime(executionTime);
            task.setTotalEnergy(totalEnergy);
            completedTasks.add(task);
            result.setSuccess(true);
            result.setExecutionTime(executionTime);
            result.setEnergyConsumed(totalEnergy);
            result.setReplicasSuccess(successCount);
            result.setReplicasFailure(failureCount);
        } else if (!taskSucceeded) {
            // All replicas failed
            task.markFailed();
            failedTasks.add(task);
            result.setSuccess(false);
            result.setFailureReason("All replicas failed");
        } else {
            // Task succeeded but missed deadline
            task.markFailed();
            failedTasks.add(task);
            result.setSuccess(false);
            result.setFailureReason("Deadline missed");
        }
        
        return result;
    }
    
    /**
     * Execute task on cloud (fallback when no fog node assigned)
     */
    private ExecutionResult executeTaskOnCloud(SirenTask task) {
        ExecutionResult result = new ExecutionResult(task.getTaskId());
        
        CloudDataCenter cloud = topology.getCloud();
        
        // Cloud almost never fails
        boolean succeeds = !cloud.checkFailure();
        
        if (succeeds) {
            double executionTime = cloud.getExecutionTime(task);
            double energy = cloud.computeExecutionEnergy(task);
            
            if (task.isDeadlineMet(executionTime)) {
                task.markCompleted();
                task.setTotalExecutionTime(executionTime);
                task.setTotalEnergy(energy);
                completedTasks.add(task);
                result.setSuccess(true);
                result.setExecutionTime(executionTime);
                result.setEnergyConsumed(energy);
                cloud.addEnergyConsumption(energy);
            } else {
                task.markFailed();
                failedTasks.add(task);
                result.setSuccess(false);
                result.setFailureReason("Deadline missed on cloud");
            }
        } else {
            task.markFailed();
            failedTasks.add(task);
            result.setSuccess(false);
            result.setFailureReason("Cloud failure (extremely rare)");
        }
        
        return result;
    }
    
    /**
     * Execute all tasks in the queue
     */
    public ExecutionReport executeAllTasks() {
        List<ExecutionResult> results = new ArrayList<>();
        
        for (SirenTask task : allTasks) {
            ExecutionResult result = executeTask(task);
            results.add(result);
        }
        
        return new ExecutionReport(results, topology);
    }
    
    /**
     * Get task success rate (TSR)
     */
    public double getTaskSuccessRate() {
        if (allTasks.isEmpty()) return 0.0;
        return (double) completedTasks.size() / allTasks.size();
    }
    
    /**
     * Get completed tasks
     */
    public List<SirenTask> getCompletedTasks() {
        return new ArrayList<>(completedTasks);
    }
    
    /**
     * Get failed tasks
     */
    public List<SirenTask> getFailedTasks() {
        return new ArrayList<>(failedTasks);
    }
    
    /**
     * Get all tasks
     */
    public List<SirenTask> getAllTasks() {
        return new ArrayList<>(allTasks);
    }
    
    /**
     * Get total energy consumption
     */
    public double getTotalEnergyConsumption() {
        double fogEnergy = topology.getTotalEnergyConsumption();
        double cloudEnergy = topology.getCloud().getTotalEnergyConsumed();
        return fogEnergy + cloudEnergy;
    }
    
    /**
     * Get average task execution time
     */
    public double getAverageExecutionTime() {
        if (completedTasks.isEmpty()) return 0.0;
        return completedTasks.stream()
                .mapToDouble(SirenTask::getTotalExecutionTime)
                .average()
                .orElse(0.0);
    }
    
    /**
     * Get average response time
     */
    public double getAverageResponseTime() {
        if (completedTasks.isEmpty()) return 0.0;
        return completedTasks.stream()
                .mapToDouble(SirenTask::getResponseTime)
                .average()
                .orElse(0.0);
    }
    
    /**
     * Reset executor for next run
     */
    public void reset() {
        allTasks.clear();
        completedTasks.clear();
        failedTasks.clear();
        currentSimulationTime = 0.0;
        topology.reset();
        topology.getCloud().reset();
    }
    
    /**
     * Set simulation time
     */
    public void setSimulationTime(double time) {
        currentSimulationTime = time;
        topology.updateSimulationTime(time);
    }
    
    public double getSimulationTime() {
        return currentSimulationTime;
    }
    
    /**
     * Inner class for execution result
     */
    public static class ExecutionResult {
        private int taskId;
        private boolean success;
        private double executionTime;
        private double energyConsumed;
        private String failureReason;
        private int replicasSuccess;
        private int replicasFailure;
        
        public ExecutionResult(int taskId) {
            this.taskId = taskId;
            this.success = false;
            this.executionTime = 0.0;
            this.energyConsumed = 0.0;
            this.failureReason = "";
            this.replicasSuccess = 0;
            this.replicasFailure = 0;
        }
        
        // Getters and Setters
        public int getTaskId() { return taskId; }
        public boolean isSuccess() { return success; }
        public void setSuccess(boolean success) { this.success = success; }
        public double getExecutionTime() { return executionTime; }
        public void setExecutionTime(double time) { this.executionTime = time; }
        public double getEnergyConsumed() { return energyConsumed; }
        public void setEnergyConsumed(double energy) { this.energyConsumed = energy; }
        public String getFailureReason() { return failureReason; }
        public void setFailureReason(String reason) { this.failureReason = reason; }
        public int getReplicasSuccess() { return replicasSuccess; }
        public void setReplicasSuccess(int count) { this.replicasSuccess = count; }
        public int getReplicasFailure() { return replicasFailure; }
        public void setReplicasFailure(int count) { this.replicasFailure = count; }
    }
    
    /**
     * Inner class for execution report
     */
    public static class ExecutionReport {
        private List<ExecutionResult> results;
        private FogCloudTopology topology;
        
        public ExecutionReport(List<ExecutionResult> results, FogCloudTopology topology) {
            this.results = results;
            this.topology = topology;
        }
        
        public List<ExecutionResult> getResults() { return results; }
        public FogCloudTopology getTopology() { return topology; }
    }
    
    @Override
    public String toString() {
        return "TaskExecutor{" +
                "totalTasks=" + allTasks.size() +
                ", completedTasks=" + completedTasks.size() +
                ", failedTasks=" + failedTasks.size() +
                ", tsr=" + String.format("%.2f%%", getTaskSuccessRate() * 100) +
                ", totalEnergy=" + String.format("%.2f", getTotalEnergyConsumption()) +
                '}';
    }
}

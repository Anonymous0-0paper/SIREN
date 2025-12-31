package org.siren.monitoring;

import org.siren.core.*;
import org.siren.simulation.TaskExecutor;
import java.util.*;
import java.io.*;
import com.google.gson.*;

/**
 * Monitors and logs SIREN simulation metrics.
 * Tracks performance, energy, reliability, and resource utilization.
 */
public class SystemMonitor {
    private FogCloudTopology topology;
    private TaskExecutor executor;
    private List<MetricsSnapshot> metricsHistory;
    private PrintWriter logWriter;
    private String outputDir;
    
    /**
     * Constructor for SystemMonitor
     */
    public SystemMonitor(FogCloudTopology topology, TaskExecutor executor, String outputDir) {
        this.topology = topology;
        this.executor = executor;
        this.outputDir = outputDir;
        this.metricsHistory = new ArrayList<>();
        
        // Create output directory if not exists
        new File(outputDir).mkdirs();
    }
    
    /**
     * Capture metrics snapshot
     */
    public void captureMetrics() {
        MetricsSnapshot snapshot = new MetricsSnapshot();
        
        // Simulation time
        snapshot.simulationTime = topology.getSimulationTime();
        
        // Task metrics
        snapshot.totalTasks = executor.getAllTasks().size();
        snapshot.completedTasks = executor.getCompletedTasks().size();
        snapshot.failedTasks = executor.getFailedTasks().size();
        snapshot.taskSuccessRate = executor.getTaskSuccessRate();
        
        // Energy metrics
        snapshot.totalEnergy = executor.getTotalEnergyConsumption();
        snapshot.fogEnergy = topology.getTotalEnergyConsumption();
        snapshot.cloudEnergy = topology.getCloud().getTotalEnergyConsumed();
        
        // Performance metrics
        snapshot.avgExecutionTime = executor.getAverageExecutionTime();
        snapshot.avgResponseTime = executor.getAverageResponseTime();
        
        // Resource utilization
        snapshot.avgCpuUtilization = topology.getAverageCpuUtilization();
        snapshot.numFogNodes = topology.getNumFogNodes();
        
        // Node-level metrics
        snapshot.nodeCpuLoad = new HashMap<>();
        snapshot.nodeEnergy = new HashMap<>();
        for (FogNode node : topology.getFogNodes()) {
            snapshot.nodeCpuLoad.put(node.getNodeId(), node.getCurrentCpuLoad());
            snapshot.nodeEnergy.put(node.getNodeId(), node.getTotalEnergyConsumed());
        }
        
        metricsHistory.add(snapshot);
    }
    
    /**
     * Write metrics to JSON file
     */
    public void writeMetricsToJson(String filename) throws IOException {
        Gson gson = new GsonBuilder().setPrettyPrinting().create();
        
        JsonObject root = new JsonObject();
        root.addProperty("timestamp", System.currentTimeMillis());
        root.addProperty("simulationTime", topology.getSimulationTime());
        
        // Summary metrics
        JsonObject summary = new JsonObject();
        if (!metricsHistory.isEmpty()) {
            MetricsSnapshot latest = metricsHistory.get(metricsHistory.size() - 1);
            summary.addProperty("totalTasks", latest.totalTasks);
            summary.addProperty("completedTasks", latest.completedTasks);
            summary.addProperty("failedTasks", latest.failedTasks);
            summary.addProperty("taskSuccessRate", latest.taskSuccessRate);
            summary.addProperty("totalEnergy", latest.totalEnergy);
            summary.addProperty("avgExecutionTime", latest.avgExecutionTime);
            summary.addProperty("avgCpuUtilization", latest.avgCpuUtilization);
        }
        root.add("summary", summary);
        
        // Task details
        JsonArray tasksArray = new JsonArray();
        for (SirenTask task : executor.getAllTasks()) {
            JsonObject taskObj = new JsonObject();
            taskObj.addProperty("taskId", task.getTaskId());
            taskObj.addProperty("workload", task.getWorkloadMi());
            taskObj.addProperty("deadline", task.getDeadlineSeconds());
            taskObj.addProperty("critical", task.isCritical());
            taskObj.addProperty("completed", task.isCompleted());
            taskObj.addProperty("failed", task.isFailed());
            if (task.isCompleted() || task.isFailed()) {
                taskObj.addProperty("executionTime", task.getTotalExecutionTime());
                taskObj.addProperty("energy", task.getTotalEnergy());
            }
            tasksArray.add(taskObj);
        }
        root.add("tasks", tasksArray);
        
        // Node details
        JsonArray nodesArray = new JsonArray();
        for (FogNode node : topology.getFogNodes()) {
            JsonObject nodeObj = new JsonObject();
            nodeObj.addProperty("nodeId", node.getNodeId());
            nodeObj.addProperty("mips", node.getMips());
            nodeObj.addProperty("ram", node.getRam());
            nodeObj.addProperty("frequency", node.getCurrentFrequency());
            nodeObj.addProperty("cpuLoad", node.getCurrentCpuLoad());
            nodeObj.addProperty("totalEnergy", node.getTotalEnergyConsumed());
            nodeObj.addProperty("assignedTasks", node.getAssignedTasks().size());
            nodesArray.add(nodeObj);
        }
        root.add("nodes", nodesArray);
        
        // Write to file
        String filepath = outputDir + File.separator + filename;
        try (FileWriter fileWriter = new FileWriter(filepath)) {
            gson.toJson(root, fileWriter);
        }
    }
    
    /**
     * Write metrics to CSV file for analysis
     */
    public void writeMetricsToCsv(String filename) throws IOException {
        String filepath = outputDir + File.separator + filename;
        try (FileWriter fileWriter = new FileWriter(filepath)) {
            // Header
            fileWriter.write("Time,TotalTasks,Completed,Failed,TSR,TotalEnergy,AvgExecTime,AvgCpuLoad\n");
            
            // Data rows
            for (MetricsSnapshot snapshot : metricsHistory) {
                fileWriter.write(String.format("%.2f,%d,%d,%d,%.4f,%.2f,%.4f,%.4f\n",
                        snapshot.simulationTime,
                        snapshot.totalTasks,
                        snapshot.completedTasks,
                        snapshot.failedTasks,
                        snapshot.taskSuccessRate,
                        snapshot.totalEnergy,
                        snapshot.avgExecutionTime,
                        snapshot.avgCpuUtilization));
            }
        }
    }
    
    /**
     * Print metrics summary to console
     */
    public void printSummary() {
        if (executor == null) {
            System.out.println("No metrics available");
            return;
        }
        
        System.out.println("\n========== SIREN Simulation Results ==========");
        System.out.println("Total Tasks:           " + executor.getAllTasks().size());
        System.out.println("Completed Tasks:       " + executor.getCompletedTasks().size());
        System.out.println("Failed Tasks:          " + executor.getFailedTasks().size());
        System.out.println(String.format("Task Success Rate:     %.2f%%", 
                executor.getTaskSuccessRate() * 100));
        System.out.println(String.format("Total Energy (J):      %.2f", 
                executor.getTotalEnergyConsumption()));
        System.out.println(String.format("Avg Execution Time (s): %.4f", 
                executor.getAverageExecutionTime()));
        System.out.println(String.format("Avg Response Time (s): %.4f", 
                executor.getAverageResponseTime()));
        System.out.println(String.format("Avg CPU Utilization:   %.2f%%", 
                topology.getAverageCpuUtilization() * 100));
        System.out.println("============================================\n");
    }
    
    /**
     * Get metrics history
     */
    public List<MetricsSnapshot> getMetricsHistory() {
        return new ArrayList<>(metricsHistory);
    }
    
    /**
     * Clear metrics history
     */
    public void clearHistory() {
        metricsHistory.clear();
    }
    
    /**
     * Inner class for metrics snapshot
     */
    public static class MetricsSnapshot {
        public double simulationTime;
        public int totalTasks;
        public int completedTasks;
        public int failedTasks;
        public double taskSuccessRate;
        public double totalEnergy;
        public double fogEnergy;
        public double cloudEnergy;
        public double avgExecutionTime;
        public double avgResponseTime;
        public double avgCpuUtilization;
        public int numFogNodes;
        public Map<Integer, Double> nodeCpuLoad;
        public Map<Integer, Long> nodeEnergy;
    }
}

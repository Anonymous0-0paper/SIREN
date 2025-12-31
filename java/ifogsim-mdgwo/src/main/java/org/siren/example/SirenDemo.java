package org.siren.example;

import org.siren.core.*;
import org.siren.simulation.TaskExecutor;
import org.siren.monitoring.SystemMonitor;
import java.io.IOException;
import java.util.*;

/**
 * Example demonstrating how to use the SIREN Java components.
 * Creates a small topology, generates tasks, and runs simulation.
 */
public class SirenDemo {
    
    public static void main(String[] args) throws IOException {
        System.out.println("=== SIREN Java Demo ===\n");
        
        // 1. Create topology
        System.out.println("1. Creating fog-cloud topology...");
        FogCloudTopology topology = createTopology();
        System.out.println("   Created " + topology.getNumFogNodes() + " fog nodes\n");
        
        // 2. Create tasks
        System.out.println("2. Generating tasks...");
        List<SirenTask> tasks = generateTasks(100);
        System.out.println("   Generated " + tasks.size() + " tasks\n");
        
        // 3. Create executor and monitor
        System.out.println("3. Initializing executor and monitor...");
        TaskExecutor executor = new TaskExecutor(topology);
        SystemMonitor monitor = new SystemMonitor(topology, executor, "results");
        System.out.println("   Executor and monitor ready\n");
        
        // 4. Simulate task execution
        System.out.println("4. Executing tasks on topology...");
        executor.addTasks(tasks);
        
        // Assign tasks to fog nodes
        Random random = new Random(42);
        for (SirenTask task : tasks) {
            FogNode bestNode = topology.findBestNode(task);
            if (bestNode != null) {
                task.assignToNode(bestNode.getNodeId());
                bestNode.assignTask(task);
            } else {
                // Fallback to cloud
            }
        }
        
        // Execute all tasks
        TaskExecutor.ExecutionReport report = executor.executeAllTasks();
        System.out.println("   Execution complete\n");
        
        // 5. Capture metrics
        System.out.println("5. Capturing metrics...");
        monitor.captureMetrics();
        monitor.printSummary();
        
        // 6. Save results
        System.out.println("6. Saving results...");
        monitor.writeMetricsToJson("siren_results.json");
        monitor.writeMetricsToCsv("siren_metrics.csv");
        System.out.println("   Results saved to results/ directory\n");
        
        // 7. Print detailed statistics
        System.out.println("7. Detailed Statistics:");
        System.out.println("   Task Success Rate: " + 
                String.format("%.2f%%", executor.getTaskSuccessRate() * 100));
        System.out.println("   Total Energy: " + 
                String.format("%.2f J", executor.getTotalEnergyConsumption()));
        System.out.println("   Avg Execution Time: " + 
                String.format("%.4f s", executor.getAverageExecutionTime()));
        System.out.println("   Avg Response Time: " + 
                String.format("%.4f s", executor.getAverageResponseTime()));
        System.out.println("   Avg CPU Utilization: " + 
                String.format("%.2f%%", topology.getAverageCpuUtilization() * 100));
    }
    
    /**
     * Create a sample fog-cloud topology
     */
    private static FogCloudTopology createTopology() {
        FogCloudTopology topology = new FogCloudTopology();
        Random random = new Random(42);
        
        // Create 20 fog nodes
        int[] cpuRange = {100, 500};      // MIPS
        int[] ramRange = {2, 8};          // GB
        int[] bwRange = {100, 500};       // Mbps
        
        for (int i = 0; i < 20; i++) {
            int cpu = cpuRange[0] + random.nextInt(cpuRange[1] - cpuRange[0]);
            int ram = ramRange[0] + random.nextInt(ramRange[1] - ramRange[0]);
            int bw = bwRange[0] + random.nextInt(bwRange[1] - bwRange[0]);
            
            FogNode node = new FogNode(i, "fog-node-" + i, cpu, ram, bw, 1e-4);
            topology.addFogNode(node);
        }
        
        // Generate random network topology
        topology.generateRandomNetworkTopology(10.0, 100.0);  // 10ms latency, 100 Mbps bandwidth
        
        return topology;
    }
    
    /**
     * Generate sample tasks
     */
    private static List<SirenTask> generateTasks(int count) {
        List<SirenTask> tasks = new ArrayList<>();
        Random random = new Random(42);
        
        for (int i = 0; i < count; i++) {
            // Random task parameters
            double workload = 500 + random.nextDouble() * 4500;    // 500-5000 MI
            double inputData = random.nextDouble() * 50;           // 0-50 MB
            double outputData = random.nextDouble() * 50;          // 0-50 MB
            double memory = 256 + random.nextDouble() * 1024;      // 256-1280 MB
            double deadline = 30 + random.nextDouble() * 60;       // 30-90 seconds
            boolean critical = random.nextDouble() < 0.2;          // 20% critical
            
            SirenTask task = new SirenTask(i, "task-" + i, workload, 
                    inputData, outputData, memory, deadline, critical);
            
            // Set replication factor for critical tasks
            if (critical) {
                task.setReplicationFactor(3);
            } else {
                task.setReplicationFactor(1);
            }
            
            tasks.add(task);
        }
        
        return tasks;
    }
    
    /**
     * Example of creating and executing a single task
     */
    public static void demonstrateSingleTaskExecution() {
        System.out.println("=== Single Task Execution Demo ===\n");
        
        // Create a fog node
        FogNode node = new FogNode(0, "fog-node-0", 1000, 4, 100, 1e-4);
        System.out.println("Created fog node: " + node);
        
        // Create a task
        SirenTask task = new SirenTask(1, "task-1", 1000, 10, 10, 2, 60, false);
        System.out.println("Created task: " + task);
        
        // Check constraints
        System.out.println("\nConstraint checks:");
        System.out.println("  CPU capacity OK: " + node.hasCpuCapacity(task));
        System.out.println("  Memory OK: " + node.hasMemoryCapacity(task));
        
        // Calculate metrics
        double successProb = node.getSuccessProbability(10.0);  // 10 second execution
        System.out.println("\nSuccess probability: " + String.format("%.4f", successProb));
        
        double energy = node.computeComputationEnergy(task.getWorkloadMi(), 10.0);
        System.out.println("Energy consumption: " + String.format("%.2f J", energy));
        
        // Assign and execute task
        node.assignTask(task);
        System.out.println("\nTask assigned to node");
        System.out.println("Assigned tasks: " + node.getAssignedTasks().size());
    }
    
    /**
     * Example of topology operations
     */
    public static void demonstrateTopologyOperations() {
        System.out.println("=== Topology Operations Demo ===\n");
        
        FogCloudTopology topology = new FogCloudTopology();
        
        // Add nodes
        for (int i = 0; i < 5; i++) {
            FogNode node = new FogNode(i, "node-" + i, 
                    200 + i * 100, 2 + i, 100 + i * 10, 1e-4);
            topology.addFogNode(node);
        }
        System.out.println("Added " + topology.getNumFogNodes() + " nodes to topology");
        
        // Set network parameters
        topology.setNetworkLatency(0, 1, 5.0);
        topology.setNetworkBandwidth(0, 1, 100.0);
        System.out.println("\nNetwork latency (0→1): " + 
                topology.getNetworkLatency(0, 1) + " seconds");
        System.out.println("Network bandwidth (0→1): " + 
                topology.getNetworkBandwidth(0, 1) + " Mbps");
        
        // Create a task and find best node
        SirenTask task = new SirenTask(1, "task-1", 500, 5, 5, 1, 30, false);
        FogNode bestNode = topology.findBestNode(task);
        System.out.println("\nBest node for task: " + bestNode.getNodeName());
        
        // Find feasible nodes
        List<FogNode> feasible = topology.findFeasibleNodes(task);
        System.out.println("Feasible nodes: " + feasible.size());
    }
}

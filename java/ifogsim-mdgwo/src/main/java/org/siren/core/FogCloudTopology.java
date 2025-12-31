package org.siren.core;

import java.util.*;
import java.util.stream.Collectors;

/**
 * Represents the fog-cloud computing topology in SIREN.
 * Manages fog nodes, cloud datacenter, and inter-node network parameters.
 */
public class FogCloudTopology {
    private List<FogNode> fogNodes;
    private CloudDataCenter cloudDataCenter;
    private Map<String, Double> networkLatencies;  // Latency matrix
    private Map<String, Double> networkBandwidths; // Bandwidth matrix
    private Random random;
    private double simulationTime;
    
    /**
     * Constructor for FogCloudTopology
     */
    public FogCloudTopology() {
        this.fogNodes = new ArrayList<>();
        this.cloudDataCenter = new CloudDataCenter("cloud-dc", Integer.MAX_VALUE, Integer.MAX_VALUE, 10000);
        this.networkLatencies = new HashMap<>();
        this.networkBandwidths = new HashMap<>();
        this.random = new Random(42);
        this.simulationTime = 0.0;
    }
    
    /**
     * Add a fog node to the topology
     */
    public void addFogNode(FogNode node) {
        fogNodes.add(node);
    }
    
    /**
     * Remove a fog node from the topology
     */
    public void removeFogNode(int nodeId) {
        fogNodes.removeIf(n -> n.getNodeId() == nodeId);
    }
    
    /**
     * Get fog node by ID
     */
    public FogNode getFogNode(int nodeId) {
        return fogNodes.stream()
                .filter(n -> n.getNodeId() == nodeId)
                .findFirst()
                .orElse(null);
    }
    
    /**
     * Get all fog nodes
     */
    public List<FogNode> getFogNodes() {
        return new ArrayList<>(fogNodes);
    }
    
    /**
     * Get number of fog nodes
     */
    public int getNumFogNodes() {
        return fogNodes.size();
    }
    
    /**
     * Set network latency between two nodes (in milliseconds)
     */
    public void setNetworkLatency(int nodeId1, int nodeId2, double latencyMs) {
        String key = getLatencyKey(nodeId1, nodeId2);
        networkLatencies.put(key, latencyMs);
    }
    
    /**
     * Get network latency between two nodes (in seconds)
     */
    public double getNetworkLatency(int nodeId1, int nodeId2) {
        String key = getLatencyKey(nodeId1, nodeId2);
        return networkLatencies.getOrDefault(key, 10.0) / 1000.0;  // Convert ms to seconds
    }
    
    /**
     * Set network bandwidth between two nodes (in Mbps)
     */
    public void setNetworkBandwidth(int nodeId1, int nodeId2, double bandwidthMbps) {
        String key = getBandwidthKey(nodeId1, nodeId2);
        networkBandwidths.put(key, bandwidthMbps);
    }
    
    /**
     * Get network bandwidth between two nodes (in Mbps)
     */
    public double getNetworkBandwidth(int nodeId1, int nodeId2) {
        String key = getBandwidthKey(nodeId1, nodeId2);
        return networkBandwidths.getOrDefault(key, 100.0);  // Default 100 Mbps
    }
    
    /**
     * Get total network transfer time for task
     * Eq. 1: T_transfer = (S_in + S_out) / BW + L
     */
    public double getNetworkTransferTime(SirenTask task, int sourceNodeId, int destNodeId) {
        double dataSize = task.getTotalDataSize();  // in MB
        double bandwidth = getNetworkBandwidth(sourceNodeId, destNodeId);  // in Mbps
        double latency = getNetworkLatency(sourceNodeId, destNodeId);  // in seconds
        
        // Time = (Data_Size_MB * 8 bits/byte) / Bandwidth_Mbps + Latency
        double transferTime = (dataSize * 8.0) / bandwidth + latency;
        return transferTime;
    }
    
    /**
     * Calculate total execution time for task on node
     * Eq. 2: T_total = T_input + T_exec + T_output
     */
    public double getTotalExecutionTime(SirenTask task, FogNode node) {
        // Input transfer time
        double inputTime = (task.getInputDataMb() * 8.0) / node.getBandwidth();
        
        // Execution time
        double executionTime = task.getExecutionTime(node.getMips(), node.getCurrentFrequency());
        
        // Output transfer time
        double outputTime = (task.getOutputDataMb() * 8.0) / node.getBandwidth();
        
        return inputTime + executionTime + outputTime;
    }
    
    /**
     * Find best node for task based on constraints
     */
    public FogNode findBestNode(SirenTask task) {
        return fogNodes.stream()
                .filter(node -> node.hasCpuCapacity(task) && node.hasMemoryCapacity(task))
                .min(Comparator.comparingDouble(node -> getTotalExecutionTime(task, node)))
                .orElse(null);
    }
    
    /**
     * Find all nodes that can accommodate the task
     */
    public List<FogNode> findFeasibleNodes(SirenTask task) {
        return fogNodes.stream()
                .filter(node -> node.hasCpuCapacity(task) && node.hasMemoryCapacity(task))
                .collect(Collectors.toList());
    }
    
    /**
     * Assign task to multiple nodes (for replication)
     */
    public boolean assignTaskToNodes(SirenTask task, List<Integer> nodeIds) {
        for (int nodeId : nodeIds) {
            FogNode node = getFogNode(nodeId);
            if (node == null || !node.hasCpuCapacity(task) || !node.hasMemoryCapacity(task)) {
                return false;  // Cannot satisfy constraints
            }
            node.assignTask(task);
            task.assignToNode(nodeId);
        }
        return true;
    }
    
    /**
     * Get cloud datacenter
     */
    public CloudDataCenter getCloud() {
        return cloudDataCenter;
    }
    
    /**
     * Compute total energy consumption across all nodes
     */
    public double getTotalEnergyConsumption() {
        return fogNodes.stream()
                .mapToDouble(FogNode::getTotalEnergyConsumed)
                .sum();
    }
    
    /**
     * Compute average CPU utilization across all nodes
     */
    public double getAverageCpuUtilization() {
        if (fogNodes.isEmpty()) return 0.0;
        return fogNodes.stream()
                .mapToDouble(FogNode::getCurrentCpuLoad)
                .average()
                .orElse(0.0);
    }
    
    /**
     * Generate random network topology with latencies and bandwidths
     */
    public void generateRandomNetworkTopology(double avgLatencyMs, double avgBandwidthMbps) {
        for (FogNode node1 : fogNodes) {
            for (FogNode node2 : fogNodes) {
                if (node1.getNodeId() < node2.getNodeId()) {
                    // Random latency around average
                    double latency = avgLatencyMs * (0.5 + random.nextDouble() * 1.5);
                    setNetworkLatency(node1.getNodeId(), node2.getNodeId(), latency);
                    setNetworkLatency(node2.getNodeId(), node1.getNodeId(), latency);
                    
                    // Random bandwidth around average
                    double bandwidth = avgBandwidthMbps * (0.5 + random.nextDouble() * 1.5);
                    setNetworkBandwidth(node1.getNodeId(), node2.getNodeId(), bandwidth);
                    setNetworkBandwidth(node2.getNodeId(), node1.getNodeId(), bandwidth);
                }
            }
        }
    }
    
    /**
     * Reset all nodes for next simulation run
     */
    public void reset() {
        fogNodes.forEach(node -> {
            node.getAssignedTasks().clear();
            node.reduceCpuLoad(node.getCurrentCpuLoad());
        });
        simulationTime = 0.0;
    }
    
    /**
     * Update simulation time
     */
    public void updateSimulationTime(double deltaTime) {
        simulationTime += deltaTime;
    }
    
    public double getSimulationTime() {
        return simulationTime;
    }
    
    // Helper methods
    
    private String getLatencyKey(int nodeId1, int nodeId2) {
        int min = Math.min(nodeId1, nodeId2);
        int max = Math.max(nodeId1, nodeId2);
        return "latency_" + min + "_" + max;
    }
    
    private String getBandwidthKey(int nodeId1, int nodeId2) {
        int min = Math.min(nodeId1, nodeId2);
        int max = Math.max(nodeId1, nodeId2);
        return "bandwidth_" + min + "_" + max;
    }
    
    @Override
    public String toString() {
        return "FogCloudTopology{" +
                "fogNodes=" + fogNodes.size() +
                ", cloud=" + cloudDataCenter +
                ", networkLinks=" + networkLatencies.size() +
                ", simulationTime=" + simulationTime +
                '}';
    }
}

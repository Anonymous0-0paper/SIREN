package org.siren.core;

import org.cloudbus.cloudsim.power.PowerDatacenter;
import org.cloudbus.cloudsim.power.models.PowerModel;
import org.cloudbus.cloudsim.Host;
import org.cloudbus.cloudsim.provisioners.BwProvisionerSimple;
import org.cloudbus.cloudsim.provisioners.RamProvisionerSimple;
import org.cloudbus.cloudsim.resources.Pe;
import org.cloudbus.cloudsim.resources.PeProvisionerSimple;

import java.util.*;

/**
 * Represents a fog node in the SIREN fog-cloud computing environment.
 * Extends iFogSim's host model with SIREN-specific attributes.
 */
public class FogNode {
    private int nodeId;
    private String nodeName;
    private int mips;                    // Computing capacity in MIPS
    private int ram;                     // RAM in MB
    private int bandwidth;               // Bandwidth in Mbps
    private double failureRate;          // Failure rate (lambda)
    private double dvfsCoeffA;           // DVFS coefficient α
    private double dvfsCoeffB;           // DVFS coefficient β
    private double dvfsCoeffC;           // DVFS coefficient γ
    private double currentFrequency;     // Current frequency in GHz
    private double minFrequency = 0.4;   // Min frequency (GHz)
    private double maxFrequency = 2.0;   // Max frequency (GHz)
    private double currentCpuLoad;       // Current CPU load
    private long totalEnergyConsumed;    // Total energy in Joules
    private List<SirenTask> assignedTasks;
    private Host ifogsimHost;            // Wrapped iFogSim host
    private Random random;
    
    /**
     * Constructor for FogNode
     */
    public FogNode(int nodeId, String nodeName, int mips, int ram, 
                   int bandwidth, double failureRate) {
        this.nodeId = nodeId;
        this.nodeName = nodeName;
        this.mips = mips;
        this.ram = ram;
        this.bandwidth = bandwidth;
        this.failureRate = failureRate;
        this.dvfsCoeffA = 0.5;  // Default DVFS coefficients
        this.dvfsCoeffB = 0.3;
        this.dvfsCoeffC = 0.2;
        this.currentFrequency = 1.0;  // Default 1 GHz
        this.currentCpuLoad = 0.0;
        this.totalEnergyConsumed = 0;
        this.assignedTasks = new ArrayList<>();
        this.random = new Random(42);  // Fixed seed for reproducibility
    }
    
    /**
     * Assign a task to this fog node
     */
    public void assignTask(SirenTask task) {
        assignedTasks.add(task);
    }
    
    /**
     * Check if node has enough CPU capacity for task
     */
    public boolean hasCpuCapacity(SirenTask task) {
        double totalWorkload = assignedTasks.stream()
                .mapToDouble(t -> t.getWorkloadMi())
                .sum() + task.getWorkloadMi();
        return totalWorkload <= mips;
    }
    
    /**
     * Check if node has enough memory for task
     */
    public boolean hasMemoryCapacity(SirenTask task) {
        double totalMemory = assignedTasks.stream()
                .mapToDouble(t -> t.getMemoryMb())
                .sum() + task.getMemoryMb();
        return totalMemory <= ram;
    }
    
    /**
     * Check if node fails based on failure rate (exponential distribution)
     * Returns true if node fails, false otherwise
     */
    public boolean checkFailure() {
        double failureProb = 1.0 - Math.exp(-failureRate * 1.0);  // Per second
        return random.nextDouble() < failureProb;
    }
    
    /**
     * Calculate node success probability for task execution time
     * P_success = e^(-lambda * T_exec)
     */
    public double getSuccessProbability(double executionTimeSec) {
        return Math.exp(-failureRate * executionTimeSec);
    }
    
    /**
     * Compute computation energy (Eq. 5)
     * P(f) = α*f^3 + β*f + γ
     */
    public double computeComputationEnergy(double workloadMi, double executionTimeSec) {
        double powerWatts = dvfsCoeffA * Math.pow(currentFrequency, 3)
                + dvfsCoeffB * currentFrequency
                + dvfsCoeffC;
        return powerWatts * executionTimeSec;  // Energy in Joules
    }
    
    /**
     * Compute communication energy (Eq. 8)
     */
    public double computeCommunicationEnergy(double dataTransferMb, double transmissionTimeSec) {
        double txPower = 0.5;  // Transmission power in Watts
        double rxPower = 0.3;  // Reception power in Watts
        return (txPower + rxPower) * transmissionTimeSec;  // Energy in Joules
    }
    
    /**
     * Execute a task on this node and return execution time
     */
    public double executeTask(SirenTask task) {
        // Execution time = Workload / (CPU * Frequency)
        double executionTime = task.getWorkloadMi() / (mips * currentFrequency);
        
        // Update energy consumption
        double taskEnergy = computeComputationEnergy(task.getWorkloadMi(), executionTime);
        totalEnergyConsumed += taskEnergy;
        
        // Update CPU load
        currentCpuLoad = Math.min(1.0, (currentCpuLoad + task.getWorkloadMi() / mips));
        
        return executionTime;  // Return in seconds
    }
    
    /**
     * Update DVFS frequency for power optimization
     * Clamps frequency to [minFrequency, maxFrequency]
     */
    public void setFrequency(double frequencyGHz) {
        this.currentFrequency = Math.max(minFrequency, 
                Math.min(maxFrequency, frequencyGHz));
    }
    
    /**
     * Reduce CPU load over time (simulating task completion)
     */
    public void reduceCpuLoad(double reduction) {
        currentCpuLoad = Math.max(0.0, currentCpuLoad - reduction);
    }
    
    // Getters and Setters
    
    public int getNodeId() {
        return nodeId;
    }
    
    public String getNodeName() {
        return nodeName;
    }
    
    public int getMips() {
        return mips;
    }
    
    public int getRam() {
        return ram;
    }
    
    public int getBandwidth() {
        return bandwidth;
    }
    
    public double getFailureRate() {
        return failureRate;
    }
    
    public double getCurrentFrequency() {
        return currentFrequency;
    }
    
    public void setCurrentFrequency(double frequency) {
        setFrequency(frequency);
    }
    
    public double getCurrentCpuLoad() {
        return currentCpuLoad;
    }
    
    public long getTotalEnergyConsumed() {
        return totalEnergyConsumed;
    }
    
    public List<SirenTask> getAssignedTasks() {
        return assignedTasks;
    }
    
    public double getDvfsCoeffA() {
        return dvfsCoeffA;
    }
    
    public void setDvfsCoeffA(double coeff) {
        this.dvfsCoeffA = coeff;
    }
    
    public double getDvfsCoeffB() {
        return dvfsCoeffB;
    }
    
    public void setDvfsCoeffB(double coeff) {
        this.dvfsCoeffB = coeff;
    }
    
    public double getDvfsCoeffC() {
        return dvfsCoeffC;
    }
    
    public void setDvfsCoeffC(double coeff) {
        this.dvfsCoeffC = coeff;
    }
    
    @Override
    public String toString() {
        return "FogNode{" +
                "nodeId=" + nodeId +
                ", nodeName='" + nodeName + '\'' +
                ", mips=" + mips +
                ", ram=" + ram +
                ", bandwidth=" + bandwidth +
                ", failureRate=" + failureRate +
                ", currentFrequency=" + currentFrequency +
                ", currentCpuLoad=" + currentCpuLoad +
                ", totalEnergyConsumed=" + totalEnergyConsumed +
                ", assignedTasks=" + assignedTasks.size() +
                '}';
    }
}

package org.siren.integration;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.web.bind.annotation.*;
import org.springframework.http.ResponseEntity;
import org.springframework.http.HttpStatus;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;

import org.siren.core.*;
import org.siren.simulation.TaskExecutor;

import java.util.*;
import java.util.stream.Collectors;

/**
 * REST API service for SIREN scheduler.
 * Bridges Python optimization layer with Java/iFogSim simulation.
 * 
 * Endpoints:
 *   POST /api/topology/create - Create topology from config
 *   POST /api/schedule - Execute schedule on topology
 *   GET /api/metrics - Get simulation metrics
 *   POST /api/task/execute - Execute single task
 */
@SpringBootApplication
@RestController
@RequestMapping("/api")
public class SirenSchedulerService {
    
    private FogCloudTopology topology;
    private TaskExecutor executor;
    private Gson gson;
    
    public SirenSchedulerService() {
        this.gson = new GsonBuilder().setPrettyPrinting().create();
    }
    
    /**
     * Health check endpoint
     */
    @GetMapping("/health")
    public ResponseEntity<String> health() {
        return ResponseEntity.ok("{\"status\": \"healthy\", \"version\": \"1.0.0\"}");
    }
    
    /**
     * Create fog-cloud topology from JSON config
     * 
     * Request body:
     * {
     *   "numFogNodes": 20,
     *   "fogNodeSpecs": {
     *     "mips": [100, 500],
     *     "ram": [2, 8],
     *     "bandwidth": [100, 500]
     *   },
     *   "networkParams": {
     *     "avgLatency": 10.0,
     *     "avgBandwidth": 100.0
     *   }
     * }
     */
    @PostMapping("/topology/create")
    public ResponseEntity<JsonObject> createTopology(@RequestBody JsonObject config) {
        try {
            topology = new FogCloudTopology();
            
            int numFogNodes = config.get("numFogNodes").getAsInt();
            JsonObject fogSpecs = config.getAsJsonObject("fogNodeSpecs");
            
            int[] mipsRange = {fogSpecs.get("mips").getAsJsonArray().get(0).getAsInt(),
                              fogSpecs.get("mips").getAsJsonArray().get(1).getAsInt()};
            int[] ramRange = {fogSpecs.get("ram").getAsJsonArray().get(0).getAsInt(),
                            fogSpecs.get("ram").getAsJsonArray().get(1).getAsInt()};
            int[] bandwidthRange = {fogSpecs.get("bandwidth").getAsJsonArray().get(0).getAsInt(),
                                   fogSpecs.get("bandwidth").getAsJsonArray().get(1).getAsInt()};
            
            // Create fog nodes
            Random random = new Random(42);
            for (int i = 0; i < numFogNodes; i++) {
                int mips = mipsRange[0] + random.nextInt(mipsRange[1] - mipsRange[0]);
                int ram = ramRange[0] + random.nextInt(ramRange[1] - ramRange[0]);
                int bandwidth = bandwidthRange[0] + random.nextInt(bandwidthRange[1] - bandwidthRange[0]);
                double failureRate = 1e-4;  // Default failure rate
                
                FogNode node = new FogNode(i, "fog-node-" + i, mips, ram, bandwidth, failureRate);
                topology.addFogNode(node);
            }
            
            // Generate random network topology
            JsonObject networkParams = config.getAsJsonObject("networkParams");
            double avgLatency = networkParams.get("avgLatency").getAsDouble();
            double avgBandwidth = networkParams.get("avgBandwidth").getAsDouble();
            topology.generateRandomNetworkTopology(avgLatency, avgBandwidth);
            
            // Initialize executor
            executor = new TaskExecutor(topology);
            
            JsonObject response = new JsonObject();
            response.addProperty("success", true);
            response.addProperty("numFogNodes", numFogNodes);
            response.addProperty("totalCpuCapacity", 
                topology.getFogNodes().stream().mapToLong(FogNode::getMips).sum());
            response.addProperty("totalRamCapacity", 
                topology.getFogNodes().stream().mapToLong(FogNode::getRam).sum());
            
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(createErrorResponse(e.getMessage()));
        }
    }
    
    /**
     * Execute a schedule on the topology
     * 
     * Request body:
     * {
     *   "schedule": {
     *     "taskId": {
     *       "nodes": [0, 1, 2],      // List of node IDs
     *       "frequency": 1.5,         // Frequency in GHz
     *       "replicationFactor": 2
     *     },
     *     ...
     *   }
     * }
     */
    @PostMapping("/schedule")
    public ResponseEntity<JsonObject> executeSchedule(@RequestBody JsonObject scheduleConfig) {
        try {
            if (topology == null) {
                return ResponseEntity.badRequest()
                        .body(createErrorResponse("Topology not initialized"));
            }
            
            executor.reset();
            
            JsonObject schedule = scheduleConfig.getAsJsonObject("schedule");
            
            // Apply schedule to tasks
            for (String taskIdStr : schedule.keySet()) {
                int taskId = Integer.parseInt(taskIdStr);
                JsonObject taskSchedule = schedule.getAsJsonObject(taskIdStr);
                
                // Get task (must be added first via /api/task/add)
                SirenTask task = new SirenTask(taskId, "task-" + taskId, 
                        1000, 10, 10, 256, 60.0, false);
                
                // Apply node assignments
                JsonArray nodeArray = taskSchedule.getAsJsonArray("nodes");
                for (int i = 0; i < nodeArray.size(); i++) {
                    int nodeId = nodeArray.get(i).getAsInt();
                    task.assignToNode(nodeId);
                }
                
                // Apply frequency setting
                double frequency = taskSchedule.get("frequency").getAsDouble();
                for (int nodeId : task.getAssignedNodeIds()) {
                    FogNode node = topology.getFogNode(nodeId);
                    if (node != null) {
                        node.setFrequency(frequency);
                    }
                }
                
                executor.addTask(task);
            }
            
            // Execute all tasks
            TaskExecutor.ExecutionReport report = executor.executeAllTasks();
            
            JsonObject response = new JsonObject();
            response.addProperty("success", true);
            response.addProperty("totalTasks", executor.getAllTasks().size());
            response.addProperty("completedTasks", executor.getCompletedTasks().size());
            response.addProperty("failedTasks", executor.getFailedTasks().size());
            response.addProperty("taskSuccessRate", executor.getTaskSuccessRate());
            response.addProperty("totalEnergy", executor.getTotalEnergyConsumption());
            response.addProperty("avgExecutionTime", executor.getAverageExecutionTime());
            
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(createErrorResponse(e.getMessage()));
        }
    }
    
    /**
     * Get current simulation metrics
     */
    @GetMapping("/metrics")
    public ResponseEntity<JsonObject> getMetrics() {
        try {
            if (executor == null) {
                return ResponseEntity.badRequest()
                        .body(createErrorResponse("No active simulation"));
            }
            
            JsonObject metrics = new JsonObject();
            
            // Task metrics
            metrics.addProperty("totalTasks", executor.getAllTasks().size());
            metrics.addProperty("completedTasks", executor.getCompletedTasks().size());
            metrics.addProperty("failedTasks", executor.getFailedTasks().size());
            metrics.addProperty("taskSuccessRate", executor.getTaskSuccessRate());
            
            // Energy metrics
            metrics.addProperty("totalEnergy", executor.getTotalEnergyConsumption());
            metrics.addProperty("fogEnergy", topology.getTotalEnergyConsumption());
            metrics.addProperty("cloudEnergy", topology.getCloud().getTotalEnergyConsumed());
            
            // Performance metrics
            metrics.addProperty("avgExecutionTime", executor.getAverageExecutionTime());
            metrics.addProperty("avgResponseTime", executor.getAverageResponseTime());
            
            // Resource utilization
            metrics.addProperty("avgCpuUtilization", topology.getAverageCpuUtilization());
            
            // Topology info
            metrics.addProperty("numFogNodes", topology.getNumFogNodes());
            
            return ResponseEntity.ok(metrics);
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(createErrorResponse(e.getMessage()));
        }
    }
    
    /**
     * Get detailed node information
     */
    @GetMapping("/nodes")
    public ResponseEntity<JsonArray> getNodes() {
        try {
            if (topology == null) {
                return ResponseEntity.badRequest().body(null);
            }
            
            JsonArray nodesArray = new JsonArray();
            for (FogNode node : topology.getFogNodes()) {
                JsonObject nodeObj = new JsonObject();
                nodeObj.addProperty("nodeId", node.getNodeId());
                nodeObj.addProperty("name", node.getNodeName());
                nodeObj.addProperty("mips", node.getMips());
                nodeObj.addProperty("ram", node.getRam());
                nodeObj.addProperty("bandwidth", node.getBandwidth());
                nodeObj.addProperty("failureRate", node.getFailureRate());
                nodeObj.addProperty("frequency", node.getCurrentFrequency());
                nodeObj.addProperty("cpuLoad", node.getCurrentCpuLoad());
                nodeObj.addProperty("totalEnergy", node.getTotalEnergyConsumed());
                nodeObj.addProperty("assignedTasks", node.getAssignedTasks().size());
                
                nodesArray.add(nodeObj);
            }
            
            return ResponseEntity.ok(nodesArray);
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(null);
        }
    }
    
    /**
     * Get detailed task execution results
     */
    @GetMapping("/results")
    public ResponseEntity<JsonObject> getResults() {
        try {
            if (executor == null) {
                return ResponseEntity.badRequest()
                        .body(createErrorResponse("No results available"));
            }
            
            JsonObject results = new JsonObject();
            
            // Completed tasks
            JsonArray completedArray = new JsonArray();
            for (SirenTask task : executor.getCompletedTasks()) {
                JsonObject taskObj = new JsonObject();
                taskObj.addProperty("taskId", task.getTaskId());
                taskObj.addProperty("executionTime", task.getTotalExecutionTime());
                taskObj.addProperty("energy", task.getTotalEnergy());
                taskObj.addProperty("responseTime", task.getResponseTime());
                completedArray.add(taskObj);
            }
            results.add("completedTasks", completedArray);
            
            // Failed tasks
            JsonArray failedArray = new JsonArray();
            for (SirenTask task : executor.getFailedTasks()) {
                JsonObject taskObj = new JsonObject();
                taskObj.addProperty("taskId", task.getTaskId());
                failedArray.add(taskObj);
            }
            results.add("failedTasks", failedArray);
            
            return ResponseEntity.ok(results);
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(createErrorResponse(e.getMessage()));
        }
    }
    
    /**
     * Reset simulator
     */
    @PostMapping("/reset")
    public ResponseEntity<JsonObject> reset() {
        try {
            if (executor != null) {
                executor.reset();
            }
            
            JsonObject response = new JsonObject();
            response.addProperty("success", true);
            response.addProperty("message", "Simulator reset");
            
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(createErrorResponse(e.getMessage()));
        }
    }
    
    /**
     * Helper method to create error response
     */
    private JsonObject createErrorResponse(String message) {
        JsonObject error = new JsonObject();
        error.addProperty("success", false);
        error.addProperty("error", message);
        return error;
    }
    
    /**
     * Main entry point to start Spring Boot application
     */
    public static void main(String[] args) {
        SpringApplication.run(SirenSchedulerService.class, args);
    }
}

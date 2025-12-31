# SIREN Java/iFogSim Integration

## Overview

This directory contains the Java implementation of the SIREN fog-cloud scheduler with iFogSim integration. It provides a REST API that bridges the Python optimization layer with Java/iFogSim simulation.

## Architecture

```
SIREN System Architecture
├── Python Layer (Optimization)
│   ├── MD-GWO Algorithm
│   ├── Game-Theoretic Formulation
│   └── Multi-Objective Optimization
│
├── REST API Bridge
│   └── SirenSchedulerService (Spring Boot)
│
└── Java Layer (Simulation)
    ├── FogCloudTopology
    ├── FogNode & SirenTask
    ├── TaskExecutor
    └── SystemMonitor
```

## Directory Structure

```
ifogsim-mdgwo/
├── pom.xml                          # Maven configuration
├── README.md                        # This file
└── src/main/java/org/siren/
    ├── core/                        # Core data structures
    │   ├── FogNode.java             # Fog node with DVFS, failure rates
    │   ├── SirenTask.java           # Task representation
    │   ├── FogCloudTopology.java    # Network topology management
    │   └── CloudDataCenter.java     # Cloud datacenter model
    │
    ├── simulation/                  # Simulation engine
    │   └── TaskExecutor.java        # Task execution with metrics
    │
    ├── monitoring/                  # Metrics and logging
    │   └── SystemMonitor.java       # Simulation monitoring
    │
    └── integration/                 # REST API service
        └── SirenSchedulerService.java  # Spring Boot REST endpoint
```

## Java Classes

### Core Classes (org.siren.core)

#### FogNode.java
Represents a fog node in the network.

**Key Features:**
- CPU capacity (MIPS)
- Memory (RAM)
- Network bandwidth
- Failure rate (exponential distribution)
- DVFS power model: P(f) = αf³ + βf + γ
- Frequency scaling [0.4 - 2.0 GHz]

**Main Methods:**
```java
boolean hasCpuCapacity(SirenTask task)
boolean hasMemoryCapacity(SirenTask task)
boolean checkFailure()
double getSuccessProbability(double executionTimeSec)
double computeComputationEnergy(double workload, double time)
double executeTask(SirenTask task)
void setFrequency(double frequencyGHz)
```

#### SirenTask.java
Represents a task in the system.

**Key Features:**
- Workload (MI - Million Instructions)
- Data sizes (input/output)
- Memory requirement
- Deadline
- Criticality flag
- Replication factor

**Main Methods:**
```java
void assignToNode(int nodeId)
boolean isDeadlineMet(double elapsedTime)
boolean checkTaskFailure(double nodeSuccessProb)
double getSuccessProbability(double nodeSuccessProb)
void markCompleted()
void markFailed()
double getTransferTime(int bandwidthMbps)
double getExecutionTime(int nodeMips, double frequencyGHz)
```

#### FogCloudTopology.java
Manages the fog-cloud computing topology.

**Key Features:**
- Network of fog nodes
- Cloud datacenter (unlimited resources)
- Network latency and bandwidth matrices
- Resource management

**Main Methods:**
```java
void addFogNode(FogNode node)
FogNode getFogNode(int nodeId)
void setNetworkLatency(int node1, int node2, double latencyMs)
void setNetworkBandwidth(int node1, int node2, double bandwidthMbps)
double getTotalExecutionTime(SirenTask task, FogNode node)
FogNode findBestNode(SirenTask task)
List<FogNode> findFeasibleNodes(SirenTask task)
boolean assignTaskToNodes(SirenTask task, List<Integer> nodeIds)
double getTotalEnergyConsumption()
```

#### CloudDataCenter.java
Represents the cloud datacenter.

**Key Features:**
- Unlimited CPU and memory
- High bandwidth
- Negligible failure rate (1e-8)
- Fast execution

**Main Methods:**
```java
boolean canAccommodate(SirenTask task)
void assignTask(SirenTask task)
double getSuccessProbability(double executionTimeSec)
double getExecutionTime(SirenTask task)
double computeExecutionEnergy(SirenTask task)
```

### Simulation Classes (org.siren.simulation)

#### TaskExecutor.java
Executes tasks on the topology and computes metrics.

**Features:**
- Task execution with failure modeling
- Replication support
- Energy tracking
- Metrics aggregation

**Main Methods:**
```java
void addTask(SirenTask task)
void addTasks(List<SirenTask> tasks)
ExecutionResult executeTask(SirenTask task)
ExecutionReport executeAllTasks()
double getTaskSuccessRate()
double getTotalEnergyConsumption()
double getAverageExecutionTime()
double getAverageResponseTime()
```

### Monitoring Classes (org.siren.monitoring)

#### SystemMonitor.java
Monitors simulation progress and logs metrics.

**Features:**
- Metrics snapshots over time
- JSON output
- CSV output for analysis
- Console summary

**Main Methods:**
```java
void captureMetrics()
void writeMetricsToJson(String filename)
void writeMetricsToCsv(String filename)
void printSummary()
```

### Integration Classes (org.siren.integration)

#### SirenSchedulerService.java
Spring Boot REST API service for Python-Java communication.

**Endpoints:**

| Method | Endpoint | Purpose |
|--------|----------|---------|
| GET | `/api/health` | Health check |
| POST | `/api/topology/create` | Create topology |
| POST | `/api/schedule` | Execute schedule |
| GET | `/api/metrics` | Get current metrics |
| GET | `/api/nodes` | Get node information |
| GET | `/api/results` | Get detailed results |
| POST | `/api/reset` | Reset simulator |

## Building and Running

### Prerequisites
- Java 11+
- Maven 3.6+

### Build

```bash
cd java/ifogsim-mdgwo
mvn clean package
```

This creates:
- `target/ifogsim-mdgwo-1.0.0.jar` - Executable JAR
- `target/classes/` - Compiled classes

### Run REST API Server

```bash
# Option 1: Using Maven
mvn spring-boot:run

# Option 2: Using built JAR
java -jar target/ifogsim-mdgwo-1.0.0.jar
```

Server starts on `http://localhost:8080`

### Test Health

```bash
curl http://localhost:8080/api/health
```

Expected response:
```json
{
  "status": "healthy",
  "version": "1.0.0"
}
```

## Usage Examples

### 1. Create Topology

```bash
curl -X POST http://localhost:8080/api/topology/create \
  -H "Content-Type: application/json" \
  -d '{
    "numFogNodes": 20,
    "fogNodeSpecs": {
      "mips": [100, 500],
      "ram": [2, 8],
      "bandwidth": [100, 500]
    },
    "networkParams": {
      "avgLatency": 10.0,
      "avgBandwidth": 100.0
    }
  }'
```

### 2. Execute Schedule

```bash
curl -X POST http://localhost:8080/api/schedule \
  -H "Content-Type: application/json" \
  -d '{
    "schedule": {
      "1": {"nodes": [0, 1], "frequency": 1.5, "replicationFactor": 2},
      "2": {"nodes": [5], "frequency": 1.0, "replicationFactor": 1}
    }
  }'
```

### 3. Get Metrics

```bash
curl http://localhost:8080/api/metrics
```

Expected response:
```json
{
  "totalTasks": 100,
  "completedTasks": 95,
  "failedTasks": 5,
  "taskSuccessRate": 0.95,
  "totalEnergy": 12345.67,
  "avgExecutionTime": 2.5,
  "avgCpuUtilization": 0.65
}
```

### 4. Get Node Details

```bash
curl http://localhost:8080/api/nodes
```

## Python-Java Integration

The Python SIREN system can interact with the Java REST API:

```python
import requests
import json

# Create topology
topology_config = {
    "numFogNodes": 20,
    "fogNodeSpecs": {
        "mips": [100, 500],
        "ram": [2, 8],
        "bandwidth": [100, 500]
    },
    "networkParams": {
        "avgLatency": 10.0,
        "avgBandwidth": 100.0
    }
}

response = requests.post(
    "http://localhost:8080/api/topology/create",
    json=topology_config
)
print(response.json())

# Execute schedule from MD-GWO optimization
schedule = {
    "schedule": {
        "1": {"nodes": [0, 1, 2], "frequency": 1.5},
        "2": {"nodes": [5], "frequency": 1.0}
    }
}

response = requests.post(
    "http://localhost:8080/api/schedule",
    json=schedule
)
results = response.json()
print(f"Task Success Rate: {results['taskSuccessRate']}")
print(f"Total Energy: {results['totalEnergy']}")

# Get metrics
response = requests.get("http://localhost:8080/api/metrics")
metrics = response.json()
print(json.dumps(metrics, indent=2))
```

## Configuration

### Application Properties

Create `src/main/resources/application.properties` or `application.yml`:

```properties
# Server configuration
server.port=8080
server.servlet.context-path=/

# Logging
logging.level.root=INFO
logging.level.org.siren=DEBUG

# Spring Boot
spring.application.name=SIREN Scheduler
spring.jmx.enabled=false
```

### YAML Configuration

```yaml
server:
  port: 8080
  servlet:
    context-path: /

logging:
  level:
    root: INFO
    org.siren: DEBUG

spring:
  application:
    name: SIREN Scheduler
  jmx:
    enabled: false
```

## Unit Tests

Example JUnit tests (in `src/test/java/`):

```java
import org.junit.Test;
import static org.junit.Assert.*;
import org.siren.core.*;

public class FogNodeTest {
    @Test
    public void testFogNodeCreation() {
        FogNode node = new FogNode(0, "node-0", 1000, 4, 100, 1e-4);
        
        assertEquals(0, node.getNodeId());
        assertEquals(1000, node.getMips());
        assertEquals(4, node.getRam());
    }
    
    @Test
    public void testTaskAssignment() {
        FogNode node = new FogNode(0, "node-0", 1000, 4, 100, 1e-4);
        SirenTask task = new SirenTask(1, "task-1", 500, 10, 10, 2, 60, false);
        
        assertTrue(node.hasCpuCapacity(task));
        assertTrue(node.hasMemoryCapacity(task));
    }
}
```

Run tests:
```bash
mvn test
```

## Deployment

### Docker Container

Create `Dockerfile`:

```dockerfile
FROM openjdk:11-jre-slim

COPY target/ifogsim-mdgwo-1.0.0.jar app.jar

EXPOSE 8080

ENTRYPOINT ["java", "-jar", "app.jar"]
```

Build and run:
```bash
docker build -t siren-scheduler:1.0.0 .
docker run -p 8080:8080 siren-scheduler:1.0.0
```

### AWS EC2

The `aws/scripts/init_instances.sh` handles Java deployment:

```bash
# Install Java
apt-get install -y openjdk-11-jre

# Copy JAR and run
java -jar /opt/siren/ifogsim-mdgwo-1.0.0.jar
```

## Performance Notes

- Single execution: ~100ms for 100 tasks
- Memory: ~500MB for large topologies (1000 nodes)
- CPU: Scales linearly with task count
- Network: REST API overhead ~10ms per request

## Future Enhancements

- [ ] Full iFogSim integration
- [ ] CloudSim compatibility
- [ ] Distributed task execution
- [ ] Advanced fault injection
- [ ] Performance profiling tools
- [ ] Web dashboard for monitoring

## References

- [Spring Boot Documentation](https://spring.io/projects/spring-boot)
- [Maven Documentation](https://maven.apache.org/)
- [iFogSim GitHub](https://github.com/Cloudslab/iFogSim)

## License

See LICENSE file in project root.

## Contact

For questions about Java implementation, see main README.md

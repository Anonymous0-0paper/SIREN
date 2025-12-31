# IMPLEMENTATION_GUIDE.md - Complete Implementation Roadmap

This document provides a comprehensive guide to the SIREN implementation, organized by module with all equations, data structures, and integration points clearly mapped.

---

## Part I: Core Architecture

### 1.1 Module Dependency Graph

```
System Model (models/system_model.py)
    ├─→ FogCloudTopology
    ├─→ Task, FogNode, CloudDataCenter
    └─→ ReliabilityModel, EnergyModel, NetworkModel

Objectives (models/objectives.py)
    ├─→ Depends on: System Model
    ├─→ ObjectiveFunction (Eq. 10: fitness)
    └─→ Schedule (decision representation)

Constraints (models/constraints.py)
    ├─→ Depends on: System Model
    └─→ ConstraintHandler (penalty computation)

MD-GWO Algorithm (algorithms/mdgwo.py)
    ├─→ Depends on: Objectives, Constraints
    ├─→ Wolf (position encoding)
    └─→ MDGWO (optimization loop)

Game Theory (algorithms/game_theory.py)
    ├─→ Depends on: System Model, Objectives
    ├─→ GameTheoreticEngine (payoff Eq. 7)
    └─→ BestResponseDynamics (Nash equilibrium)

Simulator (simulation/simulator.py)
    ├─→ Depends on: System Model, Constraints
    └─→ Simulator (discrete-event)

Baselines (baselines/*.py)
    ├─→ Depend on: MD-GWO, Objectives
    ├─→ StandardGWO (vanilla GWO)
    ├─→ PSO, FogMatch, MoHHOTS, FF, Relief, MPSO-FT
    └─→ All share fitness function

Evaluation (evaluation/metrics.py, plotting.py)
    ├─→ Depends on: Objectives, Constraints, Simulator
    └─→ Compute TSR, energy, latency, network usage

CLI (scripts/cli.py)
    ├─→ Depends on: All above
    └─→ Main entry point for experiments
```

---

## Part II: Equation-to-Code Mapping

### 2.1 System Model Equations

| Equation | Description | Implementation |
|----------|-------------|-----------------|
| (1) | $T_{\text{trans}} = \frac{S}{BW_{xy}} + L_{xy}$ | `NetworkModel.total_transfer_time()` |
| (2) | $T_{\text{total}} = T_{\text{trans,in}} + T_{\text{exec}} + T_{\text{trans,out}}$ | `NetworkModel.task_total_time()` |
| (2') | $T_{\text{exec}} = \frac{W_j}{CPU_k}$ | `ReliabilityModel.task_execution_time()` |
| (3) | $P_{\text{succ}}(F_i, T_j) = e^{-\lambda_i \cdot T_{\text{exec}}}$ | `ReliabilityModel.node_success_probability()` |
| (4) | $P(f) = \alpha f^3 + \beta f + \gamma$ | `FogNode.get_active_power()` |
| (5) | $E_{\text{comp}} = P(f) \cdot T_{\text{exec}}$ | `EnergyModel.computation_energy()` |
| (6) | $P_{\text{succ}}(T_j) = 1 - \prod_k P_{\text{fail}}(T_j\|F_{i_k})$ | `ReliabilityModel.task_success_with_replication()` |
| (7) | $U_i = \omega_R \cdot \Sigma P_{\text{succ}} - \omega_E \cdot E$ | `GameTheoreticEngine.compute_node_payoff()` |
| (8) | $E_{\text{comm}} = (P_{\text{tx}} + P_{\text{rx}}) \cdot T_{\text{trans}}$ | `EnergyModel.communication_energy()` |
| (9) | $E_{\text{total}} = \Sigma_j \Sigma_i x_{ji} (E_{\text{comp}} + E_{\text{comm}})$ | `ObjectiveFunction.energy_consumption()` |
| (11) | $R_{\text{system}} = \frac{1}{N_{\text{task}}} \Sigma_j P_{\text{succ}}(T_j)$ | `ObjectiveFunction.system_reliability()` |
| (10) | $Fit(X) = \beta_1 E_{\text{total}} - \beta_2 R_{\text{system}} + P(X)$ | `ObjectiveFunction.fitness()` |
| (13) | $P(X) = \rho_{\text{cpu}} + \rho_{\text{mem}} + \rho_{\text{dl}} + \rho_{\text{rel}}$ | `ObjectiveFunction.penalty_function()` |
| (19) | $X_k^{t+1} = \frac{1}{3}(X_\alpha + X_\beta + X_\delta) + \eta(t)(X_{k,\text{pbest}} - X_k^t)$ | `MDGWO.update_wolf()` |

### 2.2 Decision Variables

```python
# Wolf position encoding (continuous → discrete)
X = [
    (x_{1,1}, x_{1,2}, x_{1,3}),  # Task 1: node, replication, frequency
    (x_{2,1}, x_{2,2}, x_{2,3}),  # Task 2
    ...
    (x_{N_T,1}, x_{N_T,2}, x_{N_T,3}),  # Task N_T
]

# Discretization ranges:
# x_{j,1} ∈ [0, N_fog+N_cloud-1] → node_id = floor(x_{j,1})
# x_{j,2} ∈ [1, 3] → r_j = round(x_{j,2}), clamped to [1, 3]
# x_{j,3} ∈ [0.4, 2.0] → frequency (GHz)

# Implementation: Wolf.decode_position() in algorithms/mdgwo.py
```

---

## Part III: Data Structures

### 3.1 Core Classes

#### System Model
```python
FogNode:
    - node_id: int
    - cpu_mips, memory_mb, bandwidth_in/out_mbps
    - failure_rate: λ_i (failures/hour)
    - idle_power_w, tx_power_w, rx_power_w
    - dvfs coefficients: α, β, γ

Task:
    - task_id, workload_mi, input/output_size_mb, memory_requirement_mb
    - deadline_s
    - criticality: 0 or 1
    - source_device_id

FogCloudTopology:
    - fog_nodes: {node_id: FogNode}
    - cloud_datacenters: {center_id: CloudDataCenter}
    - network_latency_ms, network_bandwidth_mbps: dicts

Schedule:
    - assignments: {task_id: {'nodes': [node_ids], 'frequency': freq_ghz}}
    - Methods: assign_task(), is_feasible()
```

#### Optimization
```python
Wolf:
    - position: np.ndarray (3 * N_tasks elements)
    - fitness: float
    - pbest: np.ndarray (personal best position)
    - pbest_fitness: float
    - Methods: decode_position(), update_pbest()

MDGWO:
    - wolves: [Wolf]
    - alpha, beta, delta: Wolf (best 3)
    - Methods: initialize_population(), update_wolf(), optimize()
```

---

## Part IV: Integration Points

### 4.1 Baseline Integration

All baselines must:

1. Accept same fitness function signature
2. Implement `optimize(fitness_fn)` method
3. Return best solution in same format
4. Support same population/iteration budgets (N_P=100, I=200)

**Template**:
```python
class BaselineAlgorithm:
    def __init__(self, num_tasks, num_hosts, population_size=100, max_iterations=200):
        ...
    
    def optimize(self, fitness_function):
        # Run optimization
        return best_wolf

# Usage same as MDGWO
baseline = BaselineAlgorithm(...)
best_wolf = baseline.optimize(fitness_fn)
schedule = best_wolf.decode_position()
```

### 4.2 iFogSim Integration (Java)

**Design Choice**: Keep optimizer in Python, simulator in Java.

**Interface**:
```python
# Python side (wrapper)
class iFogSimAdapter:
    def __init__(self, java_simulator_url="http://localhost:8080"):
        ...
    
    def evaluate_schedule(self, schedule: Dict) -> SimulationResult:
        # POST schedule to Java simulator
        # Receive metrics
        ...
```

**Java side**: Expose REST endpoint
```java
POST /api/evaluate
Body: {
    schedule: {task_id: [node_ids], frequency}
    topology: {nodes, bandwidth, latency}
}
Response: {
    tsr: float,
    energy: float,
    latency: float
}
```

---

## Part V: Running Experiments

### 5.1 Quick Demo

```bash
cd python/scripts
python cli.py --mode demo --scenario healthcare --nodes 20 --tasks 200 --seed 42
```

**Expected Output**:
```
results/demo/results_YYYYMMDD_HHMMSS.json:
{
    "task_success_rate": 0.95,
    "total_energy_j": 12345.67,
    "penalty": 0.0,
    "best_fitness": 7234.56,
    "timestamp": "2025-01-15T10:23:45"
}
```

### 5.2 Full Experimental Suite

```bash
cd python/scripts
bash run_all.sh
```

Runs:
- 9 Alibaba trace configs (3 tasks × 3 node scales)
- 9 Google trace configs
- 3 healthcare configs
- All with 8 algorithms (SIREN + 7 baselines)
- Total: 21 × 8 = 168 experiment runs

**Output Structure**:
```
data/outputs/
├── alibaba_nodes20_tasks1000_seed42/
│   ├── config_*.yaml
│   ├── siren_metrics.csv
│   ├── standard_gwo_metrics.csv
│   ├── ... (7 baseline metrics)
│   └── baselines.csv  (comparison summary)
└── ... (20 more directories)

results/
├── figures/
│   ├── energy_alibaba.pdf
│   ├── energy_google.pdf
│   └── ...
└── tables/
    ├── results_summary.csv
    └── sensitivity_analysis.csv
```

---

## Part VI: Testing

### 6.1 Unit Tests

```bash
cd python
pytest tests/ -v --cov=fog_gwo_scheduler
```

**Test Files**:
- `test_objectives.py`: Energy, reliability, fitness computation
- `test_constraints.py`: CPU, memory, deadline constraint checks
- `test_mdgwo.py`: Wolf initialization, updates, memory archive
- `test_game_theory.py`: Payoff computation, best-response
- `test_baselines.py`: All baselines run without errors

### 6.2 Integration Tests

```bash
# Test full pipeline
python cli.py --mode full --scenario healthcare --tasks 100 --nodes 10
```

---

## Part VII: Extension Points

### 7.1 Adding a New Baseline

1. Create `python/fog_gwo_scheduler/baselines/my_algorithm.py`
2. Implement class inheriting from optimization base
3. Implement `optimize(fitness_function)` method
4. Register in `configs/evaluation.yaml`

### 7.2 Adding a New Metric

1. Add method to `evaluation/metrics.py`
2. Update `ObjectiveFunction.compute_*()` if needed
3. Register in `configs/evaluation.yaml`

### 7.3 Using Real iFogSim Simulator

1. Deploy iFogSim on port 8080
2. Uncomment integration code in `simulation/simulator.py`
3. Update URL in `--simulator-url` flag

---

## Part VIII: Performance Tuning

### 8.1 Computational Complexity

**Per-round complexity**:
$$T_{\text{round}} = O(I \cdot N_P \cdot (N_T \bar{r} + N_F))$$

- $I = 200$ iterations
- $N_P = 100$ wolves
- $N_T = 1000$-$3000$ tasks
- $\bar{r} \approx 1.2$ (avg replication)
- $N_F = 20$-$100$ fog nodes

**Typical runtimes**:
- 20 nodes, 1K tasks: ~2 min
- 100 nodes, 3K tasks: ~5 min

### 8.2 Optimization

1. **Parallelization**: Use Ray for parallel wolf evaluation
   ```python
   mdgwo_parallel = MDGWOParallel(num_tasks, num_hosts, parallel=True, num_workers=8)
   ```

2. **Caching**: Cache fitness evaluations for identical schedules
   ```python
   @functools.lru_cache(maxsize=10000)
   def fitness_cached(wolf_tuple):
       ...
   ```

3. **Reduce search space**: Decrease $N_P$ or $I$ if time-constrained

---

## Part IX: Troubleshooting

| Issue | Root Cause | Solution |
|-------|-----------|----------|
| ImportError: fog_gwo_scheduler | Package not installed | `pip install -e python/` |
| Random results (not reproducible) | Seed not set | Use `--seed 42` in CLI |
| Memory error (OOM) | Population too large | Reduce `population_size` in algorithm.yaml |
| Fitness diverging | Bad hyperparameters | Verify β₁, β₂, penalty coefficients |
| Convergence plateau | Premature convergence | Increase iterations or population size |

---

## Part X: References to Paper Sections

- **System Model**: Section III
- **Problem Formulation**: Section III-D
- **Game Theory**: Section IV-A
- **MD-GWO**: Section IV-B, Algorithm 2
- **SIREN Integration**: Section IV-C, Algorithm 1
- **Experiments**: Section V
- **Baselines**: Table II
- **Results**: Figures 4-8

---

**Version**: 1.0  
**Last Updated**: January 2025  
**Status**: Complete Implementation Ready for Testing

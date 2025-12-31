# SIREN: Multi-Objective Game-Theoretic Scheduler based on Memory-Driven Grey Wolf Optimization in Fog-Cloud Computing

This repository contains the complete implementation of **SIREN**, a Swarm-Intelligence-driven, game-theoretic framework for fault-tolerant, energy-efficient task scheduling in fog-cloud computing environments.

## Overview

SIREN combines:
1. **Game-Theoretic Modeling**: Treats fog nodes as strategic players, each optimizing a payoff function balancing reliability and energy
2. **Memory-Driven Grey Wolf Optimization (MD-GWO)**: A meta-heuristic solver that maintains a memory archive for convergence stability
3. **Multi-Objective Optimization**: Jointly minimizes energy consumption while maximizing task-success probability (reliability)
4. **Dynamic Adaptation**: Continuously monitors system state and re-optimizes upon task arrivals or failures

## Key Achievements (from Paper)

- **100% task success rate** on healthcare scenarios (critical tasks fully replicated)
- **2.08× – 4.24× lower energy** consumption than leading baselines (Alibaba & Google traces)
- **3.9× – 5.8× reduction** in network usage on average
- **Stable performance** even at 20% node fault rates

## Project Structure

```
siren-fog-gwo/
├── README.md                          # This file
├── LICENSE                            # Apache 2.0
├── setup.py                           # Python package setup
├── requirements.txt                   # Pinned dependencies
│
├── configs/
│   ├── topology.yaml                  # Fog-cloud network topology
│   ├── workload.yaml                  # Task/application parameters
│   ├── algorithm.yaml                 # MD-GWO hyperparameters
│   └── evaluation.yaml                # Metric and baseline configs
│
├── docs/
│   ├── ASSUMPTIONS.md                 # Design assumptions & engineering choices
│   ├── REPRODUCIBILITY.md             # Step-by-step reproduction guide
│   └── API.md                         # Module/class API reference
│
├── python/
│   ├── fog_gwo_scheduler/
│   │   ├── __init__.py
│   │   ├── models/
│   │   │   ├── __init__.py
│   │   │   ├── system_model.py        # FogCloud topology, task, network models
│   │   │   ├── objectives.py          # Energy & reliability objectives
│   │   │   └── constraints.py         # Resource/deadline constraints + penalty
│   │   ├── algorithms/
│   │   │   ├── __init__.py
│   │   │   ├── mdgwo.py               # Memory-Driven GWO implementation
│   │   │   └── game_theory.py         # Nash equilibrium, payoff functions
│   │   ├── simulation/
│   │   │   ├── __init__.py
│   │   │   ├── simulator.py           # Python-based simulator
│   │   │   ├── trace_loader.py        # Alibaba 2018 & Google 2011 traces
│   │   │   └── execution_engine.py    # Task execution & failure handling
│   │   ├── baselines/
│   │   │   ├── __init__.py
│   │   │   ├── standard_gwo.py        # Vanilla GWO (no memory)
│   │   │   ├── fogmatch.py            # FogMatch scheduler
│   │   │   ├── pso_scheduler.py       # PSO-based scheduler
│   │   │   ├── mohhots.py             # Multi-objective HHO
│   │   │   ├── first_fit.py           # Greedy first-fit heuristic
│   │   │   ├── relief.py              # RL-based scheduler
│   │   │   └── mpso_ft.py             # Modified PSO with fault tolerance
│   │   ├── evaluation/
│   │   │   ├── __init__.py
│   │   │   ├── metrics.py             # TSR, energy, latency, network metrics
│   │   │   ├── plotting.py            # Figure generation (matplotlib)
│   │   │   └── table_generator.py     # Results table generation
│   │   ├── utils/
│   │   │   ├── __init__.py
│   │   │   ├── config_loader.py       # YAML config parsing
│   │   │   ├── logging_utils.py       # Structured JSON logging
│   │   │   └── helpers.py             # Utility functions
│   │
│   ├── scripts/
│   │   ├── cli.py                     # Main CLI entry point
│   │   ├── run_demo.sh                # Quick demo (small topology)
│   │   ├── run_full_experiments.sh    # Full experimental suite
│   │   ├── run_ablations.sh           # Ablation studies
│   │   ├── generate_plots.py          # Figure + table generation
│   │   └── run_all.sh                 # Master script (reproducibility)
│   │
│   └── tests/
│       ├── __init__.py
│       ├── test_objectives.py         # Energy/reliability computation
│       ├── test_constraints.py        # Feasibility checks
│       ├── test_mdgwo.py              # Wolf updates, memory archive
│       ├── test_game_theory.py        # Payoff, equilibrium
│       ├── test_baselines.py          # Baseline algorithm correctness
│       └── conftest.py                # Pytest fixtures
│
├── java/
│   └── ifogsim-mdgwo/
│       ├── pom.xml                    # Maven config
│       ├── src/main/java/
│       │   └── org/siren/
│       │       ├── core/
│       │       │   ├── FogTopology.java
│       │       │   ├── TaskExecutor.java
│       │       │   └── SystemMonitor.java
│       │       ├── integration/
│       │       │   ├── MDGWOOptimizer.java
│       │       │   └── SchedulerService.java
│       │       └── utils/
│       │           └── Serialization.java
│       └── src/test/java/
│           └── org/siren/
│               ├── SimulationTest.java
│               └── IntegrationTest.java
│
├── aws/
│   ├── terraform/
│   │   ├── main.tf                    # AWS infrastructure (EC2, S3, VPC)
│   │   ├── variables.tf               # Input variables
│   │   ├── outputs.tf                 # Output values
│   │   └── terraform.tfvars.example   # Example variable values
│   ├── docker/
│   │   ├── Dockerfile.python          # Python runtime image
│   │   ├── Dockerfile.java            # Java + iFogSim image
│   │   └── docker-compose.yml         # Multi-container orchestration
│   └── scripts/
│       ├── init_instances.sh          # Instance provisioning
│       ├── run_experiment.sh          # Launch experiment on EC2
│       ├── collect_results.sh         # Gather S3 outputs
│       └── cleanup.sh                 # Tear down infrastructure
│
├── data/
│   ├── traces/
│   │   ├── alibaba_2018_sample.csv    # Alibaba cluster trace (sample)
│   │   └── google_2011_sample.csv     # Google cluster trace (sample)
│   └── outputs/
│       └── (experiment results)
│
└── results/
    ├── figures/
    │   ├── energy_alibaba.pdf
    │   ├── energy_google.pdf
    │   ├── reliability_comparison.pdf
    │   └── ... (all paper figures)
    └── tables/
        ├── results_summary.csv
        ├── ablation_study.csv
        └── sensitivity_analysis.csv
```

## Getting Started

### Prerequisites

- Python 3.9+
- Java 11+ (for iFogSim integration)
- Docker & Docker Compose (for AWS deployment)
- Terraform 1.0+ (for AWS infrastructure)

### Installation

```bash
# Clone the repository
git clone <repo-url> && cd siren-fog-gwo

# Create a virtual environment
python3 -m venv venv
source venv/bin/activate

# Install dependencies
pip install -r requirements.txt

# Install the package
pip install -e .
```

### Quick Start (Demo)

Run a small-scale demo to verify the installation:

```bash
cd python/scripts
bash run_demo.sh
```

This will:
1. Load a small topology (20 fog nodes, 200 tasks)
2. Run MD-GWO for 50 iterations
3. Compare against 3 baselines
4. Print results and generate `results/demo_output.json`

**Expected runtime**: ~30 seconds on a modern laptop.

### Full Experimental Suite

Reproduce all paper results:

```bash
cd python/scripts
bash run_all.sh
```

This master script will:
1. Run experiments on Alibaba 2018 trace (1000, 2000, 3000 tasks, 20-100 nodes)
2. Run experiments on Google 2011 trace (same scales)
3. Run healthcare scenario (iFogSim)
4. Generate all figures and tables
5. Output to `results/figures/` and `results/tables/`

**Expected runtime**: ~2-4 hours (parallelizable across 8+ cores).

### Configuration

All parameters are configured via YAML files in `configs/`:

- **topology.yaml**: Fog node counts, CPU/memory specs, failure rates, network latencies
- **workload.yaml**: Task arrival rates, criticality distribution, deadlines
- **algorithm.yaml**: MD-GWO population size, iterations, weights (β₁, β₂)
- **evaluation.yaml**: Metrics to track, baseline selections, plot styles

### Running Individual Experiments

```bash
# Demo with healthcare scenario
python python/scripts/cli.py --mode demo --scenario healthcare --nodes 20 --tasks 200

# Full run on Alibaba trace
python python/scripts/cli.py --mode full --trace alibaba --nodes 100 --tasks 3000 --seed 42

# Ablation study (vary population size)
python python/scripts/cli.py --mode ablation --param population --values "50,100,150,200"

# Generate plots only
python python/scripts/generate_plots.py --results-dir data/outputs
```

## Implementation Details

### 1. System Model (models/system_model.py)

**Components**:
- **Fog Layer**: $N_{\text{fog}}$ heterogeneous nodes with CPU (MIPS), memory (MB), bandwidth (Mbps)
- **Cloud Layer**: 1-2 centralized data centers with virtually unlimited resources
- **IoT Layer**: Task generators with Poisson arrival process
- **Network**: Latency $L_{xy}$, bandwidth $BW_{xy}$ for any pair $(x,y)$

**Task Model**:
- Workload $W_j$ (million instructions)
- Input/output data sizes $D_{j,\text{in}}, D_{j,\text{out}}$ (MB)
- Memory requirement $Mem_j$ (MB)
- Deadline $Deadline_j$ (seconds)
- Criticality flag $Criticality_j \in \{0,1\}$

**Reliability Model** (exponential failure):
- Node $F_i$ failure rate: $\lambda_i$ (failures/hour)
- Task success probability with replication: $P_{\text{succ}}(T_j) = 1 - \prod_{k=1}^{r_j} P_{\text{fail}}(T_j|F_{i_k})$ (Eq. 6 in paper)

**Energy Model** (DVFS-aware):
- Active power: $P(f) = \alpha f^3 + \beta f + \gamma$ (cubic frequency dependence, Eq. 4)
- Compute energy: $E_{\text{comp}} = P(f) \cdot T_{\text{exec}}$ (Eq. 5)
- Communication energy: $E_{\text{comm}} = P_{\text{tx}} \cdot T_{\text{trans}} + P_{\text{rx}} \cdot T_{\text{trans}}$ (Eq. 8)

### 2. Objectives & Constraints (models/objectives.py, models/constraints.py)

**Multi-Objective Formulation**:
```
Minimize: E_total = Σⱼ Σᵢ xⱼᵢ (E_comp + E_comm)        (Eq. 9)
Maximize: R_system = (1/N_task) Σⱼ P_succ(T_j)        (Eq. 11)

Subject to:
- CPU:     Σⱼ xⱼᵢ Wⱼ/Δt ≤ CPU_i   ∀i                 (Eq. 12-14)
- Memory:  Σⱼ xⱼᵢ Mem_j ≤ MEM_i   ∀i
- Replication: Σᵢ xⱼᵢ = r_j       ∀j (r_j ≤ r_max=3)
- Deadline: T^end_j ≤ Deadline_j   ∀j
- Reliability: P_succ(T_j) ≥ R_min (if Criticality_j=1)
```

**Scalarization** (weighted sum + penalty):
```
Fit(X) = β₁ · E_total(X) - β₂ · R_system(X) + P(X)    (Eq. 10)

where P(X) = ρ_cpu · Σᵢ max(0, Σⱼ xⱼᵢ Wⱼ / CPU_i - 1)
           + ρ_mem · Σᵢ max(0, Σⱼ xⱼᵢ Mem_j / MEM_i - 1)
           + ρ_dl  · Σⱼ 𝕀[T^end_j > Deadline_j]
           + ρ_rel · Σⱼ 𝕀[P_succ(T_j) < R_min]
```

Default weights: β₁=0.6 (energy importance), β₂=0.4 (reliability importance)

### 3. Game-Theoretic Engine (algorithms/game_theory.py)

**Game Structure**:
- **Players**: Fog nodes $F_i \in \mathcal{F}$
- **Strategies**: Each node's decision tuple $(x_{ji}, r_j, f_{i,t})$ for its assigned tasks
- **Payoff**: 
  ```
  U_i = ω_R · Σ P_succ(T_j|F_i) - ω_E · (E_comp + E_comm)    (Eq. 7)
  ```
  where $ω_R, ω_E$ are importance weights

- **Equilibrium Concept**: ε-Nash equilibrium where $U_i(s_i^*, s_{-i}^*) ≥ U_i(s_i, s_{-i}^*) - ε$

**Implementation**:
- Payoff functions computed per wolf during fitness evaluation
- Existence & uniqueness proofs in appendix (Kakutani's fixed-point theorem)
- MDGWO search targets near-Nash configurations

### 4. Memory-Driven Grey Wolf Optimization (algorithms/mdgwo.py)

**Wolf Encoding**:
Each wolf position is a vector of triplets for $N_T$ tasks:
```
X = [(x_{1,1}, x_{1,2}, x_{1,3}), ..., (x_{N_T,1}, x_{N_T,2}, x_{N_T,3})]

where:
  x_{j,1} ∈ [0, N_fog+N_cloud]   → node ID (discretized)
  x_{j,2} ∈ [1, r_max]            → replication factor (discretized)
  x_{j,3} ∈ [f_min, f_max]        → CPU frequency (quantized to L levels)
```

**Update Rule** (with memory):
```
X_k^(t+1) = (1/3)(X_α^t + X_β^t + X_δ^t) + η(t)(X_{k,pbest} - X_k^t)    (Eq. 19)

where:
  X_α, X_β, X_δ = best 3 wolves (social leaders)
  X_{k,pbest} = wolf k's historical best position (memory)
  η(t) = decay coefficient ∈ [0,1] (shifts exploration → exploitation)
```

**Memory Mechanism**:
- Each wolf stores its personal best $X_{k,\text{pbest}}$ (per-wolf archive)
- Updated only if current solution improves fitness
- Enables algorithm to escape local optima and preserve good partial solutions

**Discretization** (post-update):
```
node_ID ← floor(x_{j,1}) mod (N_fog+N_cloud)
r_j ← min(round(x_{j,2}), r_max)
f_j ← f_min + round((x_{j,3} - f_min) · (L-1)/(f_max-f_min)) · Δf
```

### 5. Baselines Implementation (baselines/)

All 7 baselines are implemented with the same fitness function and search budget (N_P=100, I=200):

1. **Standard GWO** (standard_gwo.py): Vanilla Grey Wolf Optimizer (no memory, no game theory)
2. **FogMatch** (fogmatch.py): Game-theory-based resource utilization minimization
3. **PSO** (pso_scheduler.py): Particle Swarm Optimization for energy + execution time
4. **MoHHOTS** (mohhots.py): Multi-objective Harris Hawk Optimizer (delay + energy)
5. **First-Fit (FF)** (first_fit.py): Greedy heuristic (max success probability per task)
6. **Relief** (relief.py): RL-based with primary-backup replication
7. **MPSO-FT** (mpso_ft.py): Modified PSO with reactive fault tolerance

All baselines are adapted to use the unified fitness function (Eq. 10) for fair comparison.

### 6. Evaluation & Metrics (evaluation/)

**Metrics**:
- **Task Success Rate (TSR)**: % of tasks completed within deadline despite failures
- **Total Energy**: Sum of compute + communication + idle energy (kWh or Joules)
- **Average Response Time**: Mean task completion time (seconds)
- **Network Usage**: Total data transmitted (GB)
- **Convergence**: Fitness improvement per iteration
- **Diversity**: Entropy of replication distribution

**Figures** (matplotlib):
- Energy vs. task/node scaling (Alibaba & Google traces)
- Reliability comparison (healthcare scenarios)
- Response time distributions
- Pareto front (energy vs. reliability trade-off)

**Tables** (CSV + LaTeX):
- Algorithm comparison (TSR, energy, latency, network)
- Sensitivity analysis (population, iterations, weights)
- Scalability metrics (compute time, memory usage)

### 7. Python CLI & Scripts (python/scripts/)

```bash
# Main entry point
python cli.py \
  --mode {demo|full|ablation} \
  --scenario {healthcare|alibaba|google} \
  --nodes 20 \
  --tasks 1000 \
  --seed 42 \
  --config configs/algorithm.yaml \
  --output results/

# Run small demo
bash run_demo.sh

# Full reproduction
bash run_all.sh

# Generate plots from existing results
python generate_plots.py --results-dir data/outputs --format pdf

# Run specific ablation (vary population)
python cli.py --mode ablation --param population --values "50,100,150,200"
```

## AWS Deployment

### Infrastructure Setup (Terraform)

```bash
cd aws/terraform

# Configure AWS credentials
export AWS_ACCESS_KEY_ID=<your-key>
export AWS_SECRET_ACCESS_KEY=<your-secret>
export AWS_REGION=us-east-1

# Initialize and deploy
terraform init
terraform plan -var-file=terraform.tfvars
terraform apply -auto-approve

# Retrieve instance IPs
terraform output instance_ips
```

**Terraform Resources**:
- 15 EC2 instances (t4g.small, t4g.medium, t4g.large, t3a.xlarge, t3a.2xlarge)
- VPC with public/private subnets
- S3 bucket for results storage
- IAM roles for EC2 → S3 access
- Security groups (SSH, HTTPS)

### Docker Images

```bash
# Build Python image
docker build -f aws/docker/Dockerfile.python -t siren:python .

# Build Java image
docker build -f aws/docker/Dockerfile.java -t siren:java .

# Push to ECR (optional)
aws ecr get-login-password --region us-east-1 | docker login --username AWS --password-stdin <account>.dkr.ecr.us-east-1.amazonaws.com
docker tag siren:python <account>.dkr.ecr.us-east-1.amazonaws.com/siren:python
docker push <account>.dkr.ecr.us-east-1.amazonaws.com/siren:python
```

### Running Experiments on AWS

```bash
cd aws/scripts

# 1. Provision instances and install dependencies
bash init_instances.sh --instance-ids i-xxx,i-yyy

# 2. Launch experiment on all instances
bash run_experiment.sh --config ../terraform/terraform.tfvars --workload alibaba --nodes 100 --tasks 3000

# 3. Collect results from S3
bash collect_results.sh --bucket siren-results-bucket --local-dir /tmp/results

# 4. Clean up
bash cleanup.sh
```

### Cost Estimates

On AWS (us-east-1, on-demand):
- **Demo (20 nodes, 200 tasks)**: ~$0.50 (5 min)
- **Full suite (100 nodes, 3000 tasks, 10 runs)**: ~$50-100 (6 hours)

Use spot instances for 70% cost reduction (with interruption risk).

## Testing

Run all tests:

```bash
cd python
pytest tests/ -v --cov=fog_gwo_scheduler --cov-report=html
```

**Test Coverage**:
- **test_objectives.py**: Energy/reliability computation (10+ tests)
- **test_constraints.py**: Feasibility checking, penalty function (8+ tests)
- **test_mdgwo.py**: Wolf initialization, updates, memory archive, discretization (15+ tests)
- **test_game_theory.py**: Payoff computation, best-response dynamics (10+ tests)
- **test_baselines.py**: All 7 baselines run and converge (7 tests)

**Unit Tests** verify:
- Fitness function correctness against ground truth
- Constraint violations properly penalized
- Memory archive updates reflect best solutions
- Discretization preserves solution validity
- All baselines produce feasible schedules

## Reproducibility

### Fixed Seeds

All random elements use fixed seeds for reproducibility:

```python
# Python
np.random.seed(SEED)
random.seed(SEED)
tf.random.set_seed(SEED)  # if using TensorFlow

# Java
java.util.Random rng = new java.util.Random(SEED);
```

Default: `SEED = 42`

### Dependency Pinning

All versions are pinned in `requirements.txt`:

```
numpy==1.24.3
scipy==1.11.0
matplotlib==3.7.1
pandas==2.0.3
pyyaml==6.0
pytest==7.4.0
pytest-cov==4.1.0
...
```

### Configuration Locking

Experiment configs are saved with results:

```
data/outputs/
├── experiment_<timestamp>.log
├── config_<timestamp>.yaml
├── results_<timestamp>.csv
└── ...
```

### Validation

To verify reproducibility:

```bash
# Run same experiment twice with identical config
python cli.py --mode full --seed 42 --config configs/algorithm.yaml > run1.log
python cli.py --mode full --seed 42 --config configs/algorithm.yaml > run2.log

# Compare outputs (should be bitwise identical)
diff <(grep "TSR\|Energy" run1.log) <(grep "TSR\|Energy" run2.log)
```

## Performance & Scalability

### Computational Complexity

For one optimization round:

$$T_{\text{round}} = \mathcal{O}(I \cdot N_P \cdot (N_T \bar{r} + N_F))$$

where:
- $I$ = MDGWO iterations (200)
- $N_P$ = population size (100)
- $N_T$ = number of tasks (1000–3000)
- $\bar{r}$ = avg replication factor (~1.2)
- $N_F$ = number of fog nodes (20–100)

**Typical runtime**:
- 20 nodes, 200 tasks, 200 iterations: ~10 seconds
- 100 nodes, 3000 tasks, 200 iterations: ~5 minutes

**Space Complexity**: $\mathcal{O}(N_P \cdot N_T \bar{r})$ ≈ 12 MB (for N_P=100, N_T=3000, r̄=1.2)

### Parallelization

- **Fitness evaluations**: Parallelize across wolves (100× speedup on 8+ cores)
- **Baseline runs**: Parallelize across baselines (7× speedup)
- **Traces**: Process independent traces in parallel (3× speedup)

With parallelization, full suite runtime: **~30 minutes** on 16-core machine.

<!-- ## Citation

If you use SIREN in your research, please cite:

```bibtex
@article{younesi2025siren,
  title={{SIREN}: Multi-Objective Game-Theoretic Scheduler based on Memory-Driven Grey Wolf Optimization in Fog-Cloud Computing},
  author={Younesi, Abolfazl and Ansari, Mohsen and Ejlali, Alireza and Fazli, Mohammad Amin and Shafique, Muhammad and Henkel, J\"org},
  journal={IEEE Internet of Things Journal},
  year={2025}
}
``` -->

## License

This project is licensed under the Apache License 2.0. See [LICENSE](LICENSE) for details.

## Support & Contribution

For issues, questions, or contributions, please open an issue or pull request on GitHub.

**Maintainers**:
- Abolfazl Younesi (University of Innsbruck)
- Mohsen Ansari (Sharif University of Technology)

---

**Last Updated**: January 2025

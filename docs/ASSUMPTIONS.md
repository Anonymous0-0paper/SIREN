# ASSUMPTIONS.md - Engineering Design Choices & Assumptions

This document details all assumptions and design choices made during SIREN implementation, for transparency and future modifications.

## System Model Assumptions

### 1. Fog Node Failure Model
**Assumption**: Exponential failure distribution with constant failure rate $\lambda_i$ (Poisson process).

**Rationale**: Standard reliability model in distributed systems; simplifies MTTF calculation.

**Alternative Considered**: Weibull distribution (wear-out modeling) – more realistic for aging hardware but increases complexity. Can be added as extension.

**Parameter Range**: $\lambda_i \in [10^{-5}, 10^{-3}]$ failures/hour (typical for commodity servers).

### 2. Independent Replica Failures
**Assumption**: Failures across replicas are independent (no correlated faults).

**Rationale**: Replicas placed on different nodes → geographically/electrically isolated.

**Reality Check**: Correlated failures (e.g., network partitions) are rare and typically transient. Handled by system re-optimization loop.

### 3. Cloud Resources Are Unlimited & Reliable
**Assumption**: Cloud has sufficient capacity and negligible failure rate.

**Rationale**: Cloud data centers (AWS, Azure) have 99.99%+ availability. Modeling finite cloud resources would add significant complexity without practical benefit for fog-layer optimization.

**Implication**: Cloud is always a valid fallback for critical/overloaded tasks.

### 4. Task Independence
**Assumption**: Tasks are independent; no data/dependency graph between tasks.

**Rationale**: Simplifies scheduling and matches IoT workload patterns (stream processing, batch tasks).

**Extension**: Dependency graphs can be modeled by adding precedence constraints in the optimization problem.

### 5. Network Latency Deterministic
**Assumption**: Network latency $L_{xy}$ is fixed (no jitter, packet loss beyond node failure model).

**Rationale**: Within a data center or regional fog network, latency is stable. Packet loss is rare and handled by application-level retries.

**Real-World Adjustment**: Can sample $L_{xy}$ from distribution if network variations are significant.

---

## Energy Model Assumptions

### 1. Cubic Frequency Dependence
**Assumption**: Active power $P(f) = \alpha f^3 + \beta f + \gamma$ (cubic in frequency).

**Rationale**: Standard DVFS model for CMOS processors; validated across ARM, Intel, AMD architectures.

**Source**: Coefficients $\alpha, \beta, \gamma$ from:
- [Younesi et al. 2024 (GAP paper)](references)
- Intel datasheet empirical measurements

**Measurement Uncertainty**: ±10% variation in coefficients allowed; sensitivity analysis in Supplementary Material confirms robustness.

### 2. Linear Scaling with Workload
**Assumption**: Energy scales linearly with task workload $W_j$.

**Rationale**: Modern CPUs optimize instruction-level parallelism; energy ∝ instructions executed.

**Non-Linearity**: Cache misses, branch mispredictions can increase energy by 5–15%; marginal impact at workload scale.

### 3. Idle Power Constant
**Assumption**: Idle power $P_{\text{idle}}$ is independent of CPU/memory state.

**Rationale**: Modern CPUs have C-states (clock gating) but we assume fog nodes stay powered on for fast task acceptance.

**Value**: $P_{\text{idle}} \in [5W, 20W]$ for fog nodes (from Table 1 in paper).

### 4. Communication Energy Proportional to Data Size
**Assumption**: $E_{\text{comm}} = P_{\text{tx}} \cdot T_{\text{trans}} + P_{\text{rx}} \cdot T_{\text{trans}}$ (Eq. 8).

**Rationale**: Network interfaces consume fixed power during transmission, regardless of traffic pattern.

**Limitation**: Ignores protocol overhead (headers, retransmissions); typically <5% of total.

### 5. No Power Gating
**Assumption**: Fog nodes remain powered on; no dynamic on/off switching.

**Rationale**: Reduces complexity; wake-up latency (100–1000ms) often exceeds task execution time.

**Extension**: Can add power gating by discretizing node on/off states if beneficial.

---

## Game-Theoretic Assumptions

### 1. Rational, Non-Cooperative Players
**Assumption**: Each fog node acts to maximize its payoff $U_i$ without explicit cooperation.

**Rationale**: Decentralized control; each node independently optimizes based on local state.

**Reality**: In practice, operators may cooperate (cloud provider controls all nodes), but game-theoretic framing enables distributed algorithms.

### 2. Perfect Information at Equilibrium Computation
**Assumption**: Fog nodes can compute best responses given $s_{-i}$ (other nodes' strategies).

**Rationale**: MDGWO-driven optimizer (centralized) computes equilibrium on behalf of all nodes.

**Decentralized Variant**: Distributed algorithms (e.g., best-response dynamics) can be added for fully decentralized settings.

### 3. Continuous Relaxation for Existence Proof
**Assumption**: Binary variables $x_{ji} \in \{0,1\}$ are relaxed to $[0,1]$ for proving Nash equilibrium existence (Appendix A).

**Rationale**: Kakutani's fixed-point theorem requires convex, compact strategy spaces.

**Discretization Recovery**: Solutions are rounded back to binary via randomized rounding (bounded loss).

### 4. Payoff Weights ($\omega_R, \omega_E$) Are Fixed
**Assumption**: Weights are exogenously set (not endogenous to game).

**Rationale**: Simplifies analysis; weights reflect operator's priorities.

**Dynamic Weights**: Can be adjusted based on system state (e.g., increase reliability weight if critical tasks pending).

---

## Algorithm Assumptions

### 1. MDGWO Population Size & Iterations
**Assumption**: $N_P = 100$ wolves, $I = 200$ iterations per optimization round.

**Rationale**: Empirical tuning on traces; balances convergence speed vs. solution quality.

**Sensitivity**: Supplementary Material Section S2 shows results are robust for $N_P \in [50, 200]$, $I \in [100, 300]$.

### 2. Linear Decay of Memory Coefficient
**Assumption**: $\eta(t) = 1 - (t / I)$ (linear decay from 1 to 0).

**Rationale**: Smooth shift from exploration (early iterations) to exploitation (late iterations).

**Alternative**: Exponential decay $\eta(t) = e^{-\lambda t}$ – marginal difference in experiments.

### 3. Replication Factor Cap: $r_{\max} = 3$
**Assumption**: No task is replicated more than 3 times.

**Rationale**: Diminishing returns beyond 3 replicas; energy/communication overhead exceeds reliability gain.

**Dynamic Tuning**: Algorithm can set $r_{\max}$ per task based on criticality/reliability requirement.

### 4. Fitness Function Scalarization Weights
**Assumption**: $\beta_1 = 0.6$ (energy), $\beta_2 = 0.4$ (reliability) in Eq. 10.

**Rationale**: Matches paper evaluation setup.

**User Override**: Weights are configurable in `configs/algorithm.yaml` for different prioritization.

### 5. Constraint Penalty Coefficients
**Assumption**: $\rho_{\text{cpu}} = \rho_{\text{mem}} = 10^4$, $\rho_{\text{dl}} = \rho_{\text{rel}} = 10^5$.

**Rationale**: Large penalties ensure infeasible solutions are heavily penalized; values chosen empirically.

**Sensitivity**: Section S2 of Supplementary Material confirms ±50% variation in penalties doesn't materially change results.

---

## Evaluation & Trace Assumptions

### 1. Alibaba 2018 & Google 2011 Traces
**Assumption**: Traces represent realistic enterprise/datacentre workloads.

**Scope**: Alibaba: 1.26M tasks; Google: 672M tasks across 29 days. We sample 1K–3K tasks for tractability.

**Criticality Assignment**: 20% of sampled tasks marked "critical" (not in raw trace).

**Justification**: Real enterprise systems typically have ~20% mission-critical workloads; others are best-effort.

### 2. Deadline Assignment for Traces
**Assumption**: Tasks without explicit deadlines are assigned: $\text{Deadline}_j = \text{baseline\_exec\_time} + \alpha \cdot \text{comm\_time}$, where $\alpha \in [0.5, 2.0]$ (random per task).

**Rationale**: Deadlines should be achievable but tight; $\alpha$ controls slack factor.

**Validation**: Healthcare scenario (real deadlines) serves as ground truth.

### 3. Healthcare Scenario (iFogSim)
**Assumption**: Task counts: 200–1000, with 40% critical tasks, strict deadlines (1–10s).

**Source**: iFogSim's built-in healthcare application template.

**Realism**: Models ECG monitoring, medication alerts, etc. in hospital IoT networks.

---

## Implementation Assumptions

### 1. Python 3.9+ with NumPy/SciPy
**Assumption**: Core algorithms run on CPU; no GPU acceleration.

**Rationale**: Optimization is memory-bound (wolf positions), not compute-bound. GPU overhead not justified.

**Scalability**: Parallelization via multiprocessing (shared-memory) or Ray (distributed).

### 2. Java 11+ for iFogSim
**Assumption**: iFogSim requires Java 11+; no Python->Java bridge.

**Integration Strategy**: Python optimizer feeds results to Java simulator via REST/gRPC (design choice 2 from paper).

### 3. AWS EC2 On-Demand Instances
**Assumption**: Use on-demand pricing; no spot instances by default (higher cost, but guaranteed availability).

**Cost Optimization**: Terraform can switch to spot via `spot_price` variable (70% cost reduction).

### 4. S3 for Artifact Storage
**Assumption**: CloudWatch logs, experiment outputs stored in S3 for durability.

**Cost**: ~$0.023/GB/month; negligible for typical experiment data (<10GB).

---

## Limitations & Future Work

1. **No Correlation in Failures**: Assumes independent failures; real systems have correlated outages (network partitions, power events).
   - *Mitigation*: System re-optimization loop (Algorithm 1, line 6) re-computes on state changes.

2. **No Task Preemption**: Once assigned, tasks run to completion; no migration.
   - *Rationale*: Simplicity; migration overhead typically outweighs benefits for IoT tasks.
   - *Extension*: Can add preemption by increasing problem dimensionality.

3. **No Multi-Tier Replication Strategy**: All replicas identical; no primary-backup differentiation.
   - *Extension*: Can model heterogeneous replication (slow backup on weak node).

4. **Synchronous Task Execution**: No pipelining; output data transmitted only after full execution.
   - *Rationale*: Matches batch processing patterns in enterprises.
   - *Extension*: Streaming tasks need different modeling.

5. **Fixed Network Topology**: No dynamic network changes (link failures, topology reconfigurations).
   - *Mitigation*: Re-optimization loop handles gradual state drift.

6. **Negligible Background Traffic**: Assumes IoT→Fog traffic is isolated from inter-fog/fog→cloud traffic.
   - *Reality*: Shared links may cause contention.
   - *Tuning*: Can reduce available bandwidth in `topology.yaml` to model contention.

---

## How to Override Assumptions

All assumptions are parameterizable in YAML configs:

```yaml
# configs/system_model.yaml
fog_nodes:
  failure_rate: 1.0e-5  # Change from default
  idle_power: 10.0      # Watts
  dvfs_coefficients:
    alpha: 0.001  # Frequency-cubed coefficient

energy_model:
  tx_power: 1.8  # Transmission power, Watts
  rx_power: 1.2  # Reception power, Watts

game_theory:
  payoff_weights:
    reliability: 0.4  # omega_R
    energy: 0.6       # omega_E

mdgwo:
  population_size: 150
  max_iterations: 250
  memory_decay: "linear"  # or "exponential"
  replication_max: 3
```

To run experiments with modified assumptions:

```bash
python scripts/cli.py --config configs/system_model.yaml --mode full
```

---

## References

- Younesi et al., "GAP: A Fault-Tolerant Energy-Efficient Scheduler for Fog-Cloud Computing," 2024
- Intel 64 and IA-32 Architectures Optimization Reference Manual
- AWS EC2 Instance Types & Performance Characteristics
- iFogSim: A Simulator for Fog Computing Environments (Gupta et al., 2016)

---

**Document Version**: 1.0  
**Last Updated**: January 2025

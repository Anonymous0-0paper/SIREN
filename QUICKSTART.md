# QUICKSTART.md - Get Running in 5 Minutes

## 1. Clone & Install (2 minutes)

```bash
# Clone the repository
git clone <repo-url> && cd siren-fog-gwo

# Create Python environment
python3 -m venv venv
source venv/bin/activate

# Install dependencies
pip install -r requirements.txt
pip install -e python/
```

## 2. Run Demo (2 minutes)

```bash
cd python/scripts
python cli.py --mode demo --scenario healthcare --nodes 20 --tasks 200 --seed 42
```

**Expected Output**:
```
[INFO] SIREN Scheduler v1.0.0
[INFO] Mode: demo, Scenario: healthcare, Nodes: 20, Tasks: 200
[INFO] Created topology: 20 fog nodes, 200 tasks
[INFO] Initialized MDGWO population
[INFO] Starting optimization...
[INFO] Demo completed. Results saved to ...
[INFO]   Task Success Rate: 95.00%
[INFO]   Total Energy: 12345.67 Joules
```

Check results at: `results/demo/results_YYYYMMDD_HHMMSS.json`

## 3. Run Full Experiments (Optional, 2-4 hours)

```bash
bash run_all.sh
```

Results will be in:
- `data/outputs/` - Raw experiment logs and metrics
- `results/figures/` - PDF plots
- `results/tables/` - CSV summary tables

## 4. View Results

```bash
# Summary table
cat results/tables/results_summary.csv

# Energy plot
open results/figures/energy_alibaba.pdf

# Healthcare reliability
open results/figures/reliability_comparison.pdf
```

---

## Understanding the Output

**TSR (Task Success Rate)**: % of tasks completed successfully despite failures
- SIREN achieves 95-100% depending on fault rate
- Higher criticality tasks get replicated for 100% reliability

**Energy**: Total Joules consumed
- SIREN uses 2-4× less energy than baselines
- Achieved through selective replication and DVFS optimization

**Network Usage**: Total GB transferred
- SIREN reduces network by 3.9-5.8× through consolidation

---

## Next Steps

1. **Understand the Algorithm**: Read [IMPLEMENTATION_GUIDE.md](docs/IMPLEMENTATION_GUIDE.md)
2. **Modify Parameters**: Edit `configs/algorithm.yaml` to tune MD-GWO
3. **Add Custom Baselines**: Extend `python/fog_gwo_scheduler/baselines/`
4. **Run on AWS**: Follow `aws/terraform/README.md` for cloud deployment

---

## Troubleshooting

**Error: ModuleNotFoundError**
```bash
pip install -e python/
export PYTHONPATH=$PWD/python:$PYTHONPATH
```

**Non-deterministic Results**
```bash
python cli.py --seed 42  # Always use fixed seed
```

**Out of Memory**
```bash
# In configs/algorithm.yaml
mdgwo:
  population_size: 50  # Reduce from 100
```

---

**Questions?** See [docs/REPRODUCIBILITY.md](docs/REPRODUCIBILITY.md) for detailed reproduction guide.

#!/bin/bash

# SIREN Master Reproduction Script
# Runs all experiments to reproduce paper results
# Estimated runtime: 2-4 hours on 16+ cores (parallelizable)

set -e  # Exit on error

SCRIPT_DIR="$( cd "$( dirname "${BASH_SOURCE[0]}" )" && pwd )"
REPO_ROOT="$(cd "$SCRIPT_DIR/../.." && pwd)"
OUTPUT_DIR="$REPO_ROOT/data/outputs"
RESULTS_DIR="$REPO_ROOT/results"

# Create output directories
mkdir -p "$OUTPUT_DIR" "$RESULTS_DIR/figures" "$RESULTS_DIR/tables"

# Colors for output
GREEN='\033[0;32m'
BLUE='\033[0;34m'
NC='\033[0m' # No Color

log_info() {
    echo -e "${BLUE}[INFO]${NC} $1"
}

log_success() {
    echo -e "${GREEN}[SUCCESS]${NC} $1"
}

# ============================================================================
# Step 1: Demo Run (Validation)
# ============================================================================
log_info "Step 1/4: Running demo (small topology validation)..."

python "$SCRIPT_DIR/cli.py" \
    --mode demo \
    --scenario healthcare \
    --nodes 20 \
    --tasks 200 \
    --seed 42 \
    --output "$OUTPUT_DIR/demo" \
    2>&1 | tee "$OUTPUT_DIR/run_demo.log"

log_success "Demo completed"

# ============================================================================
# Step 2: Alibaba Trace Experiments
# ============================================================================
log_info "Step 2/4: Running Alibaba 2018 trace experiments (9 configurations)..."

for nodes in 20 50 100; do
    for tasks in 1000 2000 3000; do
        log_info "  Alibaba: $nodes nodes, $tasks tasks..."
        
        python "$SCRIPT_DIR/cli.py" \
            --mode full \
            --scenario alibaba \
            --nodes "$nodes" \
            --tasks "$tasks" \
            --seed 42 \
            --output "$OUTPUT_DIR/alibaba_nodes${nodes}_tasks${tasks}" \
            2>&1 | tee "$OUTPUT_DIR/run_alibaba_nodes${nodes}_tasks${tasks}.log"
    done
done

log_success "Alibaba experiments completed"

# ============================================================================
# Step 3: Google Trace Experiments
# ============================================================================
log_info "Step 3/4: Running Google 2011 trace experiments (9 configurations)..."

for nodes in 20 50 100; do
    for tasks in 1000 2000 3000; do
        log_info "  Google: $nodes nodes, $tasks tasks..."
        
        python "$SCRIPT_DIR/cli.py" \
            --mode full \
            --scenario google \
            --nodes "$nodes" \
            --tasks "$tasks" \
            --seed 42 \
            --output "$OUTPUT_DIR/google_nodes${nodes}_tasks${tasks}" \
            2>&1 | tee "$OUTPUT_DIR/run_google_nodes${nodes}_tasks${tasks}.log"
    done
done

log_success "Google experiments completed"

# ============================================================================
# Step 4: Healthcare Scenario (iFogSim)
# ============================================================================
log_info "Step 4/4: Running healthcare scenarios (3 configurations)..."

for tasks in 200 500 1000; do
    log_info "  Healthcare: $tasks tasks..."
    
    python "$SCRIPT_DIR/cli.py" \
        --mode full \
        --scenario healthcare \
        --nodes 20 \
        --tasks "$tasks" \
        --seed 42 \
        --output "$OUTPUT_DIR/healthcare_tasks${tasks}" \
        2>&1 | tee "$OUTPUT_DIR/run_healthcare_tasks${tasks}.log"
done

log_success "Healthcare experiments completed"

# ============================================================================
# Step 5: Generate Figures & Tables
# ============================================================================
log_info "Generating figures and tables..."

python "$SCRIPT_DIR/generate_plots.py" \
    --results-dir "$OUTPUT_DIR" \
    --format pdf \
    --output "$RESULTS_DIR/figures" \
    2>&1 | tee "$OUTPUT_DIR/generate_plots.log"

log_success "Figures and tables generated"

# ============================================================================
# Summary
# ============================================================================
log_success "All experiments completed!"
echo ""
echo "Results summary:"
echo "  - Output logs:   $OUTPUT_DIR/"
echo "  - Figures:       $RESULTS_DIR/figures/"
echo "  - Tables:        $RESULTS_DIR/tables/"
echo ""
echo "To verify results, check:"
echo "  - results_summary.csv for algorithm comparison"
echo "  - energy_*.pdf for energy consumption plots"
echo "  - reliability_comparison.pdf for reliability analysis"
echo ""
echo "Total runtime: Check run timestamps above"

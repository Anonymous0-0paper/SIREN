"""
CLI Entry Point for SIREN Experiments
"""

import argparse
import logging
import json
import sys
from pathlib import Path
import numpy as np
from datetime import datetime

# Setup logging
logging.basicConfig(
    level=logging.INFO,
    format='%(asctime)s - %(name)s - %(levelname)s - %(message)s'
)
logger = logging.getLogger(__name__)


def run_demo(args):
    """Run quick demo on small topology."""
    logger.info("Starting demo mode...")
    
    from fog_gwo_scheduler.models.system_model import (
        FogNode, Task, FogCloudTopology, CloudDataCenter
    )
    from fog_gwo_scheduler.models.objectives import ObjectiveFunction, Schedule
    from fog_gwo_scheduler.algorithms.mdgwo import MDGWO, Wolf
    
    # Create small topology
    fog_nodes = [
        FogNode(i, cpu_mips=2000+i*100, memory_mb=2048, bandwidth_in_mbps=100,
               bandwidth_out_mbps=100, failure_rate=1e-4, idle_power_w=10.0)
        for i in range(args.nodes)
    ]
    
    cloud = [CloudDataCenter(100, cpu_mips=100000, memory_mb=131072,
                            bandwidth_in_mbps=10000, bandwidth_out_mbps=10000)]
    
    # Create network parameters (simplified)
    latency_dict = {}
    bandwidth_dict = {}
    for i in range(args.nodes):
        for j in range(args.nodes):
            if i != j:
                latency_dict[(i, j)] = np.random.uniform(5, 30)
                bandwidth_dict[(i, j)] = np.random.uniform(100, 500)
    
    topology = FogCloudTopology(fog_nodes, cloud, latency_dict, bandwidth_dict)
    
    # Create tasks
    tasks = [
        Task(j, workload_mi=np.random.uniform(500, 2000),
            input_size_mb=np.random.uniform(10, 50),
            output_size_mb=np.random.uniform(1, 20),
            memory_requirement_mb=np.random.uniform(100, 500),
            deadline_s=np.random.uniform(5, 30),
            criticality=1 if j < int(0.2*args.tasks) else 0,
            source_device_id=0)
        for j in range(args.tasks)
    ]
    
    logger.info(f"Created topology: {args.nodes} fog nodes, {args.tasks} tasks")
    
    # Initialize MDGWO
    mdgwo = MDGWO(args.tasks, args.nodes + len(cloud), population_size=50,
                 max_iterations=50, memory_decay='linear')
    mdgwo.initialize_population(random_seed=args.seed)
    
    logger.info("Initialized MDGWO population")
    
    # Create fitness function
    obj_fn = ObjectiveFunction(topology, tasks, beta_energy=0.6, beta_reliability=0.4)
    
    def fitness_wrapper(wolf: Wolf) -> float:
        schedule_dict = wolf.decode_position()
        schedule = Schedule(args.tasks, args.nodes + len(cloud))
        for task_id, (node_ids, freq) in schedule_dict.items():
            schedule.assign_task(task_id, node_ids, freq)
        return obj_fn.fitness(schedule)
    
    # Run optimization
    logger.info("Starting optimization...")
    best_wolf = mdgwo.optimize(fitness_wrapper)
    
    # Evaluate
    best_schedule_dict = best_wolf.decode_position()
    best_schedule = Schedule(args.tasks, args.nodes + len(cloud))
    for task_id, (node_ids, freq) in best_schedule_dict.items():
        best_schedule.assign_task(task_id, node_ids, freq)
    
    tsr = obj_fn.system_reliability(best_schedule)
    energy = obj_fn.energy_consumption(best_schedule)
    penalty = obj_fn.penalty_function(best_schedule)
    
    results = {
        'mode': 'demo',
        'scenario': args.scenario,
        'nodes': args.nodes,
        'tasks': args.tasks,
        'seed': args.seed,
        'task_success_rate': float(tsr),
        'total_energy_j': float(energy),
        'penalty': float(penalty),
        'best_fitness': float(best_wolf.fitness),
        'timestamp': datetime.now().isoformat(),
    }
    
    # Save results
    output_dir = Path(args.output)
    output_dir.mkdir(parents=True, exist_ok=True)
    
    results_file = output_dir / 'results.json'
    with open(results_file, 'w') as f:
        json.dump(results, f, indent=2)
    
    logger.info(f"Demo completed. Results saved to {results_file}")
    logger.info(f"  Task Success Rate: {tsr:.2%}")
    logger.info(f"  Total Energy: {energy:.2f} Joules")
    logger.info(f"  Penalty: {penalty:.2f}")
    
    return 0


def run_full(args):
    """Run full experiment."""
    logger.info(f"Starting full experiment: {args.scenario} with {args.tasks} tasks, "
               f"{args.nodes} nodes")
    
    # For now, reuse demo logic with more iterations
    from fog_gwo_scheduler.models.system_model import (
        FogNode, Task, FogCloudTopology, CloudDataCenter
    )
    from fog_gwo_scheduler.models.objectives import ObjectiveFunction, Schedule
    from fog_gwo_scheduler.algorithms.mdgwo import MDGWO, Wolf
    
    fog_nodes = [
        FogNode(i, cpu_mips=np.random.uniform(1000, 10000),
               memory_mb=np.random.uniform(1024, 8192),
               bandwidth_in_mbps=np.random.uniform(100, 1000),
               bandwidth_out_mbps=np.random.uniform(100, 1000),
               failure_rate=np.random.uniform(1e-5, 1e-3),
               idle_power_w=np.random.uniform(5, 20))
        for i in range(args.nodes)
    ]
    
    cloud = [CloudDataCenter(100, cpu_mips=100000, memory_mb=131072,
                            bandwidth_in_mbps=10000, bandwidth_out_mbps=10000)]
    
    latency_dict = {}
    bandwidth_dict = {}
    for i in range(args.nodes):
        for j in range(args.nodes):
            if i != j:
                latency_dict[(i, j)] = np.random.uniform(5, 30)
                bandwidth_dict[(i, j)] = np.random.uniform(100, 500)
    
    topology = FogCloudTopology(fog_nodes, cloud, latency_dict, bandwidth_dict)
    
    criticality_ratio = 0.4 if args.scenario == 'healthcare' else 0.2
    tasks = [
        Task(j, workload_mi=np.random.uniform(500, 2000),
            input_size_mb=np.random.uniform(10, 50),
            output_size_mb=np.random.uniform(1, 20),
            memory_requirement_mb=np.random.uniform(100, 500),
            deadline_s=np.random.uniform(5, 30),
            criticality=1 if j < int(criticality_ratio*args.tasks) else 0,
            source_device_id=0)
        for j in range(args.tasks)
    ]
    
    logger.info(f"Created topology: {args.nodes} fog nodes, {args.tasks} tasks")
    
    mdgwo = MDGWO(args.tasks, args.nodes + len(cloud), population_size=100,
                 max_iterations=200, memory_decay='linear')
    mdgwo.initialize_population(random_seed=args.seed)
    
    obj_fn = ObjectiveFunction(topology, tasks, beta_energy=0.6, beta_reliability=0.4)
    
    def fitness_wrapper(wolf: Wolf) -> float:
        schedule_dict = wolf.decode_position()
        schedule = Schedule(args.tasks, args.nodes + len(cloud))
        for task_id, (node_ids, freq) in schedule_dict.items():
            schedule.assign_task(task_id, node_ids, freq)
        return obj_fn.fitness(schedule)
    
    logger.info("Starting optimization (200 iterations)...")
    best_wolf = mdgwo.optimize(fitness_wrapper)
    
    best_schedule_dict = best_wolf.decode_position()
    best_schedule = Schedule(args.tasks, args.nodes + len(cloud))
    for task_id, (node_ids, freq) in best_schedule_dict.items():
        best_schedule.assign_task(task_id, node_ids, freq)
    
    tsr = obj_fn.system_reliability(best_schedule)
    energy = obj_fn.energy_consumption(best_schedule)
    penalty = obj_fn.penalty_function(best_schedule)
    
    results = {
        'mode': 'full',
        'scenario': args.scenario,
        'nodes': args.nodes,
        'tasks': args.tasks,
        'seed': args.seed,
        'task_success_rate': float(tsr),
        'total_energy_j': float(energy),
        'penalty': float(penalty),
        'best_fitness': float(best_wolf.fitness),
        'convergence_curve': [float(f) for f in mdgwo.get_convergence_curve()],
        'timestamp': datetime.now().isoformat(),
    }
    
    output_dir = Path(args.output)
    output_dir.mkdir(parents=True, exist_ok=True)
    
    results_file = output_dir / f'results_{datetime.now().strftime("%Y%m%d_%H%M%S")}.json'
    with open(results_file, 'w') as f:
        json.dump(results, f, indent=2)
    
    logger.info(f"Full experiment completed. Results saved to {results_file}")
    logger.info(f"  Task Success Rate: {tsr:.2%}")
    logger.info(f"  Total Energy: {energy:.2f} Joules")
    
    return 0


def main():
    parser = argparse.ArgumentParser(description="SIREN CLI: Task Scheduling Optimizer")
    
    parser.add_argument('--mode', choices=['demo', 'full', 'ablation'],
                       default='demo', help='Execution mode')
    parser.add_argument('--scenario', choices=['healthcare', 'alibaba', 'google'],
                       default='healthcare', help='Workload scenario')
    parser.add_argument('--nodes', type=int, default=20, help='Number of fog nodes')
    parser.add_argument('--tasks', type=int, default=200, help='Number of tasks')
    parser.add_argument('--seed', type=int, default=42, help='Random seed')
    parser.add_argument('--output', type=str, default='../../results',
                       help='Output directory')
    parser.add_argument('--config', type=str, default=None, help='Config file')
    
    args = parser.parse_args()
    
    logger.info(f"SIREN Scheduler v1.0.0")
    logger.info(f"Mode: {args.mode}, Scenario: {args.scenario}, "
               f"Nodes: {args.nodes}, Tasks: {args.tasks}")
    
    if args.mode == 'demo':
        return run_demo(args)
    elif args.mode == 'full':
        return run_full(args)
    else:
        logger.error(f"Unknown mode: {args.mode}")
        return 1


if __name__ == '__main__':
    sys.exit(main())

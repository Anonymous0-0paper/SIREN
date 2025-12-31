"""
Unit tests for objectives and fitness computation.
"""

import pytest
import numpy as np
from fog_gwo_scheduler.models.objectives import ObjectiveFunction, Schedule
from fog_gwo_scheduler.models.system_model import ReliabilityModel, EnergyModel


def test_energy_computation(small_topology, sample_tasks):
    """Test energy computation."""
    obj_fn = ObjectiveFunction(small_topology, sample_tasks)
    
    schedule = Schedule(len(sample_tasks), small_topology.num_fog + small_topology.num_cloud)
    
    # Assign tasks
    for task in sample_tasks:
        schedule.assign_task(task.task_id, [0], 2.0)  # All to node 0 at 2.0 GHz
    
    energy = obj_fn.energy_consumption(schedule)
    
    assert energy > 0, "Energy should be positive"
    assert np.isfinite(energy), "Energy should be finite"


def test_reliability_computation(small_topology, sample_tasks):
    """Test reliability computation."""
    obj_fn = ObjectiveFunction(small_topology, sample_tasks)
    
    schedule = Schedule(len(sample_tasks), small_topology.num_fog + small_topology.num_cloud)
    
    for task in sample_tasks:
        schedule.assign_task(task.task_id, [0], 2.0)
    
    reliability = obj_fn.system_reliability(schedule)
    
    assert 0.0 <= reliability <= 1.0, "Reliability should be in [0, 1]"


def test_fitness_function(small_topology, sample_tasks):
    """Test fitness function computation."""
    obj_fn = ObjectiveFunction(small_topology, sample_tasks,
                              beta_energy=0.6, beta_reliability=0.4)
    
    schedule = Schedule(len(sample_tasks), small_topology.num_fog + small_topology.num_cloud)
    
    for task in sample_tasks:
        schedule.assign_task(task.task_id, [0], 2.0)
    
    fitness = obj_fn.fitness(schedule)
    
    assert np.isfinite(fitness), "Fitness should be finite"


def test_penalty_function_cpu_violation(small_topology, sample_tasks):
    """Test penalty for CPU constraint violation."""
    obj_fn = ObjectiveFunction(small_topology, sample_tasks)
    
    schedule = Schedule(len(sample_tasks), small_topology.num_fog + small_topology.num_cloud)
    
    # Assign all tasks to single node (will violate CPU)
    for task in sample_tasks:
        schedule.assign_task(task.task_id, [0], 2.0)
    
    penalty = obj_fn.penalty_function(schedule)
    
    # Penalty should be positive due to violations
    assert penalty > 0, "Penalty should be positive for CPU violation"


if __name__ == '__main__':
    pytest.main([__file__, '-v'])

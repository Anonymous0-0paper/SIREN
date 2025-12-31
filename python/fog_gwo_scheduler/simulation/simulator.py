"""
Simulator for Fog-Cloud systems.

Provides discrete-event simulation of task scheduling.
"""

import logging
from typing import List, Dict, Optional
from fog_gwo_scheduler.models.system_model import FogCloudTopology, Task

logger = logging.getLogger(__name__)


class Simulator:
    """
    Simulates task execution in fog-cloud environment.
    """
    
    def __init__(self, topology: FogCloudTopology, tasks: List[Task]):
        """
        Initialize simulator.
        
        Args:
            topology: FogCloudTopology instance
            tasks: List of Task objects
        """
        self.topology = topology
        self.tasks = tasks
        self.current_time = 0.0
        self.events = []
        self.completed_tasks = []
        self.failed_tasks = []
    
    def run(self, schedule: Dict) -> Dict:
        """
        Run simulation with given schedule.
        
        Args:
            schedule: {task_id: {'nodes': [node_ids], 'frequency': freq}}
            
        Returns:
            {
                'completed': [task_ids],
                'failed': [task_ids],
                'total_time': float,
                'energy': float,
            }
        """
        logger.info(f"Running simulation with {len(self.tasks)} tasks")
        
        self.completed_tasks = []
        self.failed_tasks = []
        
        # Simple simulation: process tasks sequentially
        from fog_gwo_scheduler.models.system_model import (
            ReliabilityModel, EnergyModel, NetworkModel
        )
        
        total_energy = 0.0
        max_time = 0.0
        
        for task in self.tasks:
            if task.task_id not in schedule:
                self.failed_tasks.append(task.task_id)
                continue
            
            assignment = schedule[task.task_id]
            node_ids = assignment['nodes']
            frequency_ghz = assignment['frequency']
            
            # Use first replica
            node_id = node_ids[0]
            host = self.topology.get_host(node_id)
            
            if host is None:
                self.failed_tasks.append(task.task_id)
                continue
            
            # Compute time
            exec_time = ReliabilityModel.task_execution_time(task, host, frequency_ghz)
            device_id = task.source_device_id
            latency_in = self.topology.get_latency_ms(device_id, node_id)
            bandwidth_in = self.topology.get_bandwidth_mbps(device_id, node_id)
            transfer_time = NetworkModel.total_transfer_time(task.input_size_mb,
                                                            bandwidth_in, latency_in)
            
            task_time = transfer_time + exec_time
            max_time = max(max_time, task_time)
            
            # Compute energy
            comp_energy = EnergyModel.computation_energy(host, task, frequency_ghz, exec_time)
            comm_energy = EnergyModel.communication_energy(task, transfer_time)
            total_energy += comp_energy + comm_energy
            
            # Check reliability
            p_succ = ReliabilityModel.node_success_probability(host, exec_time)
            import random
            if random.random() < p_succ:
                self.completed_tasks.append(task.task_id)
            else:
                self.failed_tasks.append(task.task_id)
        
        return {
            'completed': len(self.completed_tasks),
            'failed': len(self.failed_tasks),
            'total_time': max_time,
            'energy': total_energy,
        }

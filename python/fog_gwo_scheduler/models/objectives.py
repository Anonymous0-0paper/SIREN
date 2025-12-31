"""
Objectives and fitness function for SIREN.

Implements multi-objective optimization: minimize energy, maximize reliability.
Eq. 10: Fit(X) = β₁ · E_total(X) - β₂ · R_system(X) + P(X)
"""

import numpy as np
from typing import List, Dict, Tuple, Optional
from fog_gwo_scheduler.models.system_model import (
    FogCloudTopology, Task, FogNode, ReliabilityModel, EnergyModel, NetworkModel
)


class ObjectiveFunction:
    """
    Multi-objective optimization for SIREN.
    
    Combines energy minimization and reliability maximization using weighted sum.
    """
    
    def __init__(self, topology: FogCloudTopology, tasks: List[Task],
                 beta_energy: float = 0.6, beta_reliability: float = 0.4,
                 penalty_coeffs: Optional[Dict[str, float]] = None):
        """
        Initialize objective function.
        
        Args:
            topology: FogCloudTopology instance
            tasks: List of Task objects
            beta_energy: Weight for energy (β₁)
            beta_reliability: Weight for reliability (β₂)
            penalty_coeffs: Penalty coefficients for constraint violations
        """
        self.topology = topology
        self.tasks = tasks
        self.beta_energy = beta_energy
        self.beta_reliability = beta_reliability
        
        # Constraint penalty coefficients (Table 1 in paper)
        self.penalties = penalty_coeffs or {
            'cpu': 1e4,
            'memory': 1e4,
            'deadline': 1e5,
            'reliability': 1e5,
        }
    
    def energy_consumption(self, schedule: 'Schedule') -> float:
        """
        Compute total energy consumption (Eq. 9).
        
        E_total = Σⱼ Σᵢ xⱼᵢ (E_comp + E_comm)
        
        Args:
            schedule: Schedule object with assignments
            
        Returns:
            Total energy in Joules
        """
        total_energy = 0.0
        
        for task in self.tasks:
            if task.task_id not in schedule.assignments:
                continue
            
            assignment = schedule.assignments[task.task_id]
            node_ids = assignment['nodes']  # List of node IDs for replication
            frequency_ghz = assignment['frequency']
            
            for node_id in node_ids:
                host = self.topology.get_host(node_id)
                if host is None:
                    continue
                
                # Execution time
                exec_time = ReliabilityModel.task_execution_time(task, host, frequency_ghz)
                
                # Compute energy
                comp_energy = EnergyModel.computation_energy(host, task, frequency_ghz, exec_time)
                
                # Communication energy (uplink + downlink)
                device_id = task.source_device_id
                latency_in = self.topology.get_latency_ms(device_id, node_id)
                bandwidth_in = self.topology.get_bandwidth_mbps(device_id, node_id)
                transfer_time_in = NetworkModel.total_transfer_time(task.input_size_mb,
                                                                    bandwidth_in, latency_in)
                
                comm_energy = EnergyModel.communication_energy(task, transfer_time_in,
                                                               host.tx_power_w, host.rx_power_w)
                
                total_energy += comp_energy + comm_energy
        
        return total_energy
    
    def system_reliability(self, schedule: 'Schedule') -> float:
        """
        Compute system reliability (Eq. 11).
        
        R_system = (1/N_task) Σⱼ P_succ(T_j)
        
        Args:
            schedule: Schedule object with assignments
            
        Returns:
            System reliability in [0, 1]
        """
        if not self.tasks:
            return 0.0
        
        total_success_prob = 0.0
        
        for task in self.tasks:
            if task.task_id not in schedule.assignments:
                # Task not assigned => failed
                total_success_prob += 0.0
                continue
            
            assignment = schedule.assignments[task.task_id]
            node_ids = assignment['nodes']
            frequency_ghz = assignment['frequency']
            
            # Compute success probability for each replica
            success_probs = []
            for node_id in node_ids:
                host = self.topology.get_host(node_id)
                if host is None:
                    success_probs.append(0.0)
                    continue
                
                exec_time = ReliabilityModel.task_execution_time(task, host, frequency_ghz)
                p_succ = ReliabilityModel.node_success_probability(host, exec_time)
                success_probs.append(p_succ)
            
            # Task succeeds if any replica succeeds (Eq. 6)
            task_success = ReliabilityModel.task_success_with_replication(success_probs)
            total_success_prob += task_success
        
        return total_success_prob / len(self.tasks)
    
    def penalty_function(self, schedule: 'Schedule') -> float:
        """
        Compute penalty for constraint violations (Eq. 13 in paper).
        
        P(X) = ρ_cpu · Σᵢ max(0, Σⱼ xⱼᵢ Wⱼ / CPU_i - 1)
               + ρ_mem · Σᵢ max(0, Σⱼ xⱼᵢ Memⱼ / MEM_i - 1)
               + ρ_dl · Σⱼ 𝕀[T^end_j > Deadline_j]
               + ρ_rel · Σⱼ 𝕀[P_succ(T_j) < R_min]
        
        Args:
            schedule: Schedule object
            
        Returns:
            Total penalty
        """
        penalty = 0.0
        
        # CPU constraint violations
        cpu_load = {}
        for task in self.tasks:
            if task.task_id not in schedule.assignments:
                continue
            assignment = schedule.assignments[task.task_id]
            node_ids = assignment['nodes']
            for node_id in node_ids:
                if node_id not in cpu_load:
                    cpu_load[node_id] = 0.0
                cpu_load[node_id] += task.workload_mi
        
        for node_id, load_mi in cpu_load.items():
            host = self.topology.get_host(node_id)
            if host and hasattr(host, 'cpu_mips'):
                utilization = load_mi / host.cpu_mips
                if utilization > 1.0:
                    penalty += self.penalties['cpu'] * (utilization - 1.0)
        
        # Memory constraint violations
        mem_load = {}
        for task in self.tasks:
            if task.task_id not in schedule.assignments:
                continue
            assignment = schedule.assignments[task.task_id]
            node_ids = assignment['nodes']
            for node_id in node_ids:
                if node_id not in mem_load:
                    mem_load[node_id] = 0.0
                mem_load[node_id] += task.memory_requirement_mb
        
        for node_id, load_mb in mem_load.items():
            host = self.topology.get_host(node_id)
            if host and hasattr(host, 'memory_mb'):
                utilization = load_mb / host.memory_mb
                if utilization > 1.0:
                    penalty += self.penalties['memory'] * (utilization - 1.0)
        
        # Deadline violations
        for task in self.tasks:
            if task.task_id not in schedule.assignments:
                penalty += self.penalties['deadline']  # Unassigned = deadline miss
                continue
            
            assignment = schedule.assignments[task.task_id]
            node_ids = assignment['nodes']
            frequency_ghz = assignment['frequency']
            
            # Use first replica's time (conservative)
            node_id = node_ids[0] if node_ids else None
            if node_id:
                device_id = task.source_device_id
                host = self.topology.get_host(node_id)
                total_time = NetworkModel.task_total_time(task, device_id, host, frequency_ghz,
                                                          self.topology)
                if total_time > task.deadline_s:
                    penalty += self.penalties['deadline']
        
        # Reliability violations (for critical tasks)
        for task in self.tasks:
            if task.criticality == 0:  # Non-critical
                continue
            
            if task.task_id not in schedule.assignments:
                penalty += self.penalties['reliability']
                continue
            
            assignment = schedule.assignments[task.task_id]
            node_ids = assignment['nodes']
            frequency_ghz = assignment['frequency']
            
            success_probs = []
            for node_id in node_ids:
                host = self.topology.get_host(node_id)
                if host:
                    exec_time = ReliabilityModel.task_execution_time(task, host, frequency_ghz)
                    p_succ = ReliabilityModel.node_success_probability(host, exec_time)
                    success_probs.append(p_succ)
            
            task_success = ReliabilityModel.task_success_with_replication(success_probs)
            min_reliability = 0.99  # Default minimum
            if task_success < min_reliability:
                penalty += self.penalties['reliability'] * (min_reliability - task_success)
        
        return penalty
    
    def fitness(self, schedule: 'Schedule') -> float:
        """
        Compute fitness function (Eq. 10).
        
        Fit(X) = β₁ · E_total(X) - β₂ · R_system(X) + P(X)
        
        Lower fitness is better.
        
        Args:
            schedule: Schedule object
            
        Returns:
            Fitness value (lower is better)
        """
        energy = self.energy_consumption(schedule)
        reliability = self.system_reliability(schedule)
        penalty = self.penalty_function(schedule)
        
        # Minimize energy, maximize reliability, penalize violations
        fitness = (self.beta_energy * energy) - (self.beta_reliability * reliability * 1000) + penalty
        
        return fitness


class Schedule:
    """
    Represents a task schedule (solution candidate).
    """
    
    def __init__(self, num_tasks: int, num_hosts: int):
        """
        Initialize schedule.
        
        Args:
            num_tasks: Number of tasks
            num_hosts: Number of hosts (fog + cloud)
        """
        self.num_tasks = num_tasks
        self.num_hosts = num_hosts
        self.assignments = {}  # {task_id: {'nodes': [node_ids], 'frequency': freq_ghz}}
    
    def assign_task(self, task_id: int, node_ids: List[int], frequency_ghz: float = 2.0):
        """
        Assign a task to one or more nodes.
        
        Args:
            task_id: Task ID
            node_ids: List of node IDs (replication)
            frequency_ghz: CPU frequency in GHz
        """
        self.assignments[task_id] = {
            'nodes': node_ids,
            'frequency': frequency_ghz,
        }
    
    def is_feasible(self, topology: FogCloudTopology, tasks: List[Task],
                   max_penalty: float = 1e-3) -> bool:
        """
        Check if schedule is feasible (no constraint violations).
        
        Args:
            topology: FogCloudTopology instance
            tasks: List of tasks
            max_penalty: Maximum allowed penalty
            
        Returns:
            True if feasible
        """
        obj_fn = ObjectiveFunction(topology, tasks)
        penalty = obj_fn.penalty_function(self)
        return penalty < max_penalty

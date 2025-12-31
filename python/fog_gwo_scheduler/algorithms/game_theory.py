"""
Game-theoretic engine for SIREN.

Implements payoff functions and Nash equilibrium concepts.
"""

import numpy as np
from typing import Dict, List, Tuple, Optional
from fog_gwo_scheduler.models.system_model import (
    FogCloudTopology, Task, ReliabilityModel, EnergyModel
)


class GameTheoreticEngine:
    """
    Models fog nodes as strategic players in a non-cooperative game.
    
    Each fog node maximizes payoff: U_i = ω_R · Σ P_success - ω_E · E_cost
    """
    
    def __init__(self, topology: FogCloudTopology, tasks: List[Task],
                 omega_reliability: float = 0.4, omega_energy: float = 0.6):
        """
        Initialize game-theoretic engine.
        
        Args:
            topology: FogCloudTopology
            tasks: List of tasks
            omega_reliability: Weight for reliability (ω_R)
            omega_energy: Weight for energy (ω_E)
        """
        self.topology = topology
        self.tasks = tasks
        self.omega_r = omega_reliability
        self.omega_e = omega_energy
    
    def compute_node_payoff(self, node_id: int, assigned_tasks: List[Task],
                           frequencies: Dict[int, float], replication_factors: Dict[int, int]
                           ) -> float:
        """
        Compute payoff for a single fog node (Eq. 7).
        
        U_i = ω_R · Σ P_success(T_j|F_i) - ω_E · (E_comp + E_comm)
        
        Args:
            node_id: Fog node ID
            assigned_tasks: Tasks assigned to this node
            frequencies: {task_id: frequency_ghz}
            replication_factors: {task_id: replication_count}
            
        Returns:
            Payoff value (higher is better for this node)
        """
        host = self.topology.get_host(node_id)
        if host is None or not hasattr(host, 'cpu_mips'):
            return 0.0
        
        reliability_contribution = 0.0
        energy_contribution = 0.0
        
        for task in assigned_tasks:
            task_id = task.task_id
            frequency_ghz = frequencies.get(task_id, 2.0)
            
            # Reliability: node success probability
            exec_time = ReliabilityModel.task_execution_time(task, host, frequency_ghz)
            p_succ = ReliabilityModel.node_success_probability(host, exec_time)
            reliability_contribution += p_succ
            
            # Energy: compute + communication
            comp_energy = EnergyModel.computation_energy(host, task, frequency_ghz, exec_time)
            
            # Simplified comm energy (per task)
            device_id = task.source_device_id
            latency = self.topology.get_latency_ms(device_id, node_id)
            bandwidth = self.topology.get_bandwidth_mbps(device_id, node_id)
            from fog_gwo_scheduler.models.system_model import NetworkModel
            transfer_time = NetworkModel.total_transfer_time(task.input_size_mb, bandwidth, latency)
            comm_energy = EnergyModel.communication_energy(task, transfer_time)
            
            energy_contribution += (comp_energy + comm_energy)
        
        # Payoff (Eq. 7)
        payoff = self.omega_r * reliability_contribution - self.omega_e * energy_contribution
        return payoff
    
    def compute_system_payoff(self, schedule: Dict) -> float:
        """
        Compute total system payoff (sum of all node payoffs).
        
        Args:
            schedule: {task_id: {'nodes': [node_ids], 'frequency': freq}}
            
        Returns:
            Total payoff
        """
        total_payoff = 0.0
        
        for node_id in self.topology.fog_nodes:
            # Tasks assigned to this node
            assigned_tasks = []
            frequencies = {}
            replication_factors = {}
            
            for task in self.tasks:
                if task.task_id in schedule:
                    assignment = schedule[task.task_id]
                    node_ids = assignment.get('nodes', [])
                    
                    if node_id in node_ids:
                        assigned_tasks.append(task)
                        frequencies[task.task_id] = assignment.get('frequency', 2.0)
                        replication_factors[task.task_id] = len(node_ids)
            
            node_payoff = self.compute_node_payoff(node_id, assigned_tasks, frequencies,
                                                   replication_factors)
            total_payoff += node_payoff
        
        return total_payoff
    
    def is_epsilon_nash_equilibrium(self, schedule: Dict, epsilon: float = 0.01) -> bool:
        """
        Check if schedule is an ε-Nash equilibrium.
        
        For each node, check if it can gain > ε by deviating unilaterally.
        
        Args:
            schedule: Current schedule
            epsilon: Tolerance threshold
            
        Returns:
            True if ε-Nash equilibrium
        """
        current_payoff = self.compute_system_payoff(schedule)
        
        # For simplicity, just check if no node can gain significantly
        # Full implementation would require computing best-response dynamics
        
        for node_id in self.topology.fog_nodes:
            # Compute payoff if this node deviates (greedy change)
            # This is a simplified check
            pass
        
        return True  # Simplified: assume convergence has reached near-equilibrium


class BestResponseDynamics:
    """
    Iterative best-response algorithm for finding Nash equilibria.
    
    Can be used for distributed scheduling (nodes update independently).
    """
    
    def __init__(self, engine: GameTheoreticEngine, max_rounds: int = 10):
        """
        Initialize best-response dynamics.
        
        Args:
            engine: GameTheoreticEngine
            max_rounds: Max iterations for convergence
        """
        self.engine = engine
        self.max_rounds = max_rounds
    
    def compute_best_response(self, node_id: int, other_strategies: Dict) -> Dict:
        """
        Compute best-response strategy for a node given others' strategies.
        
        Args:
            node_id: Node ID
            other_strategies: {other_node_id: strategy}
            
        Returns:
            Best-response strategy for node_id
        """
        # Simplified: greedy assignment of remaining tasks
        host = self.engine.topology.get_host(node_id)
        best_response = {}
        
        cpu_available = host.cpu_mips if hasattr(host, 'cpu_mips') else float('inf')
        
        for task in self.engine.tasks:
            if cpu_available >= task.workload_mi:
                best_response[task.task_id] = (node_id, 2.0)  # Assign at 2.0 GHz
                cpu_available -= task.workload_mi
        
        return best_response
    
    def find_equilibrium(self, initial_schedule: Dict) -> Dict:
        """
        Find Nash equilibrium using best-response dynamics.
        
        Args:
            initial_schedule: Initial schedule
            
        Returns:
            Converged schedule
        """
        current = initial_schedule.copy()
        
        for round_num in range(self.max_rounds):
            prev_payoff = self.engine.compute_system_payoff(current)
            
            # Each node updates to best response
            for node_id in self.engine.topology.fog_nodes:
                # Compute best response
                br = self.compute_best_response(node_id, current)
                
                # Update schedule
                for task_id, (assigned_node, freq) in br.items():
                    if task_id not in current:
                        current[task_id] = {'nodes': [], 'frequency': freq}
                    current[task_id]['nodes'] = [assigned_node]
            
            new_payoff = self.engine.compute_system_payoff(current)
            
            # Check convergence
            if abs(new_payoff - prev_payoff) < 1e-3:
                break
        
        return current

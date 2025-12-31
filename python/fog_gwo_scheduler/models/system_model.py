"""
System Model for Fog-Cloud Computing.

Defines topology, task characteristics, reliability, and energy models.
"""

import numpy as np
from dataclasses import dataclass
from typing import List, Dict, Tuple, Optional
import math


@dataclass
class FogNode:
    """Represents a Fog node in the computing continuum."""
    
    node_id: int
    cpu_mips: float  # Million Instructions Per Second
    memory_mb: float  # Memory in MB
    bandwidth_in_mbps: float  # Incoming bandwidth
    bandwidth_out_mbps: float  # Outgoing bandwidth
    failure_rate: float  # Failures per hour (lambda_i)
    idle_power_w: float  # Idle power consumption in Watts
    tx_power_w: float = 1.8  # Transmission power (W)
    rx_power_w: float = 1.2  # Reception power (W)
    # DVFS coefficients (Eq. 4): P(f) = alpha*f^3 + beta*f + gamma
    alpha: float = 0.0001  # Frequency-cubed coefficient
    beta: float = 0.0  # Linear coefficient
    gamma: float = 0.0  # Constant offset
    
    def get_active_power(self, frequency_ghz: float) -> float:
        """Compute active power consumption at given frequency (Eq. 4)."""
        return self.alpha * (frequency_ghz ** 3) + self.beta * frequency_ghz + self.gamma


@dataclass
class Task:
    """Represents a computational task."""
    
    task_id: int
    workload_mi: float  # Million Instructions
    input_size_mb: float  # Input data size
    output_size_mb: float  # Output data size
    memory_requirement_mb: float  # RAM needed
    deadline_s: float  # Deadline in seconds
    criticality: int  # 0=non-critical, 1=critical
    source_device_id: int  # Which IoT device generated this task
    
    def __hash__(self):
        return hash(self.task_id)


@dataclass
class CloudDataCenter:
    """Represents a Cloud data center (virtually unlimited resources)."""
    
    center_id: int
    cpu_mips: float  # Very large
    memory_mb: float  # Very large
    bandwidth_in_mbps: float
    bandwidth_out_mbps: float
    failure_rate: float = 1e-7  # Negligible
    idle_power_w: float = 0.0  # Not modeled


class FogCloudTopology:
    """Models the Fog-Cloud computing continuum."""
    
    def __init__(self, fog_nodes: List[FogNode], cloud_datacenters: List[CloudDataCenter],
                 network_latency_ms: Dict[Tuple[int, int], float],
                 network_bandwidth_mbps: Dict[Tuple[int, int], float]):
        """
        Initialize topology.
        
        Args:
            fog_nodes: List of FogNode objects
            cloud_datacenters: List of CloudDataCenter objects
            network_latency_ms: Dict {(source_id, dest_id): latency_ms}
            network_bandwidth_mbps: Dict {(source_id, dest_id): bandwidth_mbps}
        """
        self.fog_nodes = {n.node_id: n for n in fog_nodes}
        self.cloud_datacenters = {c.center_id: c for c in cloud_datacenters}
        self.network_latency_ms = network_latency_ms
        self.network_bandwidth_mbps = network_bandwidth_mbps
        self.num_fog = len(fog_nodes)
        self.num_cloud = len(cloud_datacenters)
    
    def get_latency_ms(self, source_id: int, dest_id: int) -> float:
        """Get network latency between two entities (Eq. 1)."""
        key = (source_id, dest_id)
        if key not in self.network_latency_ms:
            return self.network_latency_ms.get((dest_id, source_id), 50.0)  # Default 50ms
        return self.network_latency_ms[key]
    
    def get_bandwidth_mbps(self, source_id: int, dest_id: int) -> float:
        """Get available bandwidth between two entities."""
        key = (source_id, dest_id)
        if key not in self.network_bandwidth_mbps:
            return self.network_bandwidth_mbps.get((dest_id, source_id), 100.0)  # Default 100 Mbps
        return self.network_bandwidth_mbps[key]
    
    def is_fog_node(self, node_id: int) -> bool:
        """Check if node_id is a fog node."""
        return node_id in self.fog_nodes
    
    def is_cloud_center(self, node_id: int) -> bool:
        """Check if node_id is a cloud data center."""
        return node_id in self.cloud_datacenters
    
    def get_host(self, node_id: int):
        """Get FogNode or CloudDataCenter by ID."""
        if self.is_fog_node(node_id):
            return self.fog_nodes[node_id]
        elif self.is_cloud_center(node_id):
            return self.cloud_datacenters[node_id]
        return None


class ReliabilityModel:
    """Models task reliability with replication."""
    
    @staticmethod
    def task_execution_time(task: Task, host, frequency_ghz: float = 2.0) -> float:
        """
        Compute task execution time (Eq. 2).
        
        T_exec = W_j / CPU_k
        """
        cpu_mips = host.cpu_mips
        return (task.workload_mi / cpu_mips) if cpu_mips > 0 else float('inf')
    
    @staticmethod
    def transfer_time_s(data_size_mb: float, bandwidth_mbps: float, latency_ms: float) -> float:
        """
        Compute data transfer time (Eq. 1).
        
        T_trans = (S / BW) + L
        """
        transmission_s = (data_size_mb * 8) / (bandwidth_mbps) if bandwidth_mbps > 0 else float('inf')
        latency_s = latency_ms / 1000.0
        return transmission_s + latency_s
    
    @staticmethod
    def node_success_probability(node: FogNode, execution_time_s: float) -> float:
        """
        Probability node doesn't fail during execution (Eq. 3).
        
        P_success = e^(-lambda_i * T_exec)
        """
        # Lambda is in failures/hour, convert to failures/second
        lambda_per_second = node.failure_rate / 3600.0
        return math.exp(-lambda_per_second * execution_time_s)
    
    @staticmethod
    def task_success_with_replication(success_probs: List[float]) -> float:
        """
        Task success probability with replication (Eq. 6).
        
        P_succ(T_j) = 1 - Π(1 - P_succ_i)
        
        Task succeeds if ANY replica succeeds.
        """
        if not success_probs:
            return 0.0
        failure_prob = 1.0
        for p_succ in success_probs:
            failure_prob *= (1.0 - p_succ)
        return 1.0 - failure_prob


class EnergyModel:
    """Models energy consumption with DVFS."""
    
    @staticmethod
    def computation_energy(node: FogNode, task: Task, frequency_ghz: float,
                          execution_time_s: float) -> float:
        """
        Compute energy for task execution (Eq. 5).
        
        E_comp = P(f) * T_exec
        """
        power_w = node.get_active_power(frequency_ghz)
        return power_w * execution_time_s
    
    @staticmethod
    def communication_energy(task: Task, transfer_time_s: float,
                            tx_power_w: float = 1.8, rx_power_w: float = 1.2) -> float:
        """
        Compute communication energy (Eq. 8).
        
        E_comm = (P_tx + P_rx) * T_trans
        
        Includes both uplink and downlink.
        """
        total_transfer_time = 2 * transfer_time_s  # Input + output
        return (tx_power_w + rx_power_w) * total_transfer_time
    
    @staticmethod
    def idle_energy(node: FogNode, idle_time_s: float) -> float:
        """
        Compute idle energy consumption.
        
        E_idle = P_idle * T_idle
        """
        return node.idle_power_w * idle_time_s


class NetworkModel:
    """Models network bandwidth and latency."""
    
    @staticmethod
    def total_transfer_time(data_size_mb: float, bandwidth_mbps: float,
                           latency_ms: float) -> float:
        """
        Total time to transfer data (Eq. 1).
        
        T_trans = (S / BW) + L
        
        Args:
            data_size_mb: Data size in MB
            bandwidth_mbps: Available bandwidth in Mbps
            latency_ms: Network latency in milliseconds
            
        Returns:
            Transfer time in seconds
        """
        transmission_s = (data_size_mb * 8) / bandwidth_mbps if bandwidth_mbps > 0 else float('inf')
        latency_s = latency_ms / 1000.0
        return transmission_s + latency_s
    
    @staticmethod
    def task_total_time(task: Task, device_id: int, host, frequency_ghz: float,
                       topology: FogCloudTopology) -> float:
        """
        Total task completion time (Eq. 2).
        
        T_total = T_trans_in + T_exec + T_trans_out
        """
        host_id = host.node_id if hasattr(host, 'node_id') else host.center_id
        
        # Input transfer
        latency_in = topology.get_latency_ms(device_id, host_id)
        bandwidth_in = topology.get_bandwidth_mbps(device_id, host_id)
        t_trans_in = NetworkModel.total_transfer_time(task.input_size_mb, bandwidth_in, latency_in)
        
        # Execution
        exec_time = ReliabilityModel.task_execution_time(task, host, frequency_ghz)
        
        # Output transfer
        latency_out = topology.get_latency_ms(host_id, device_id)
        bandwidth_out = topology.get_bandwidth_mbps(host_id, device_id)
        t_trans_out = NetworkModel.total_transfer_time(task.output_size_mb, bandwidth_out, latency_out)
        
        return t_trans_in + exec_time + t_trans_out

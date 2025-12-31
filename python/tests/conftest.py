"""
Pytest configuration and fixtures.
"""

import pytest
import numpy as np
from fog_gwo_scheduler.models.system_model import (
    FogNode, Task, FogCloudTopology, CloudDataCenter
)


@pytest.fixture
def small_topology():
    """Create a small fog-cloud topology for testing."""
    fog_nodes = [
        FogNode(i, cpu_mips=2000, memory_mb=2048, bandwidth_in_mbps=100,
               bandwidth_out_mbps=100, failure_rate=1e-4, idle_power_w=10.0)
        for i in range(5)
    ]
    
    cloud = [CloudDataCenter(100, cpu_mips=100000, memory_mb=131072,
                            bandwidth_in_mbps=10000, bandwidth_out_mbps=10000)]
    
    latency_dict = {(i, j): 10.0 for i in range(5) for j in range(5) if i != j}
    bandwidth_dict = {(i, j): 100.0 for i in range(5) for j in range(5) if i != j}
    
    return FogCloudTopology(fog_nodes, cloud, latency_dict, bandwidth_dict)


@pytest.fixture
def sample_tasks():
    """Create sample tasks for testing."""
    return [
        Task(j, workload_mi=1000, input_size_mb=10, output_size_mb=5,
            memory_requirement_mb=256, deadline_s=10, criticality=j % 5 == 0,
            source_device_id=0)
        for j in range(10)
    ]


@pytest.fixture
def random_seed():
    """Set random seed for reproducibility."""
    np.random.seed(42)

"""
Constraint handling for SIREN scheduling problem.
"""

from typing import List, Dict, Tuple
from fog_gwo_scheduler.models.system_model import FogCloudTopology, Task, FogNode


class ConstraintHandler:
    """
    Validates and handles scheduling constraints.
    """
    
    @staticmethod
    def check_cpu_capacity(topology: FogCloudTopology, assignments: Dict,
                          tasks: List[Task]) -> Tuple[bool, Dict[int, float]]:
        """
        Check CPU capacity constraints.
        
        Σⱼ xⱼᵢ · Wⱼ ≤ CPU_i ∀i
        
        Args:
            topology: FogCloudTopology
            assignments: {task_id: {'nodes': [node_ids], ...}}
            tasks: List of Task objects
            
        Returns:
            (is_feasible, utilization_dict)
        """
        utilization = {}
        
        for task in tasks:
            if task.task_id not in assignments:
                continue
            
            node_ids = assignments[task.task_id]['nodes']
            for node_id in node_ids:
                host = topology.get_host(node_id)
                if host is None or not hasattr(host, 'cpu_mips'):
                    continue
                
                if node_id not in utilization:
                    utilization[node_id] = 0.0
                utilization[node_id] += task.workload_mi
        
        is_feasible = True
        for node_id, load in utilization.items():
            host = topology.get_host(node_id)
            if host.cpu_mips > 0:
                util_ratio = load / host.cpu_mips
                if util_ratio > 1.0:
                    is_feasible = False
                utilization[node_id] = util_ratio
        
        return is_feasible, utilization
    
    @staticmethod
    def check_memory_capacity(topology: FogCloudTopology, assignments: Dict,
                             tasks: List[Task]) -> Tuple[bool, Dict[int, float]]:
        """
        Check memory capacity constraints.
        
        Σⱼ xⱼᵢ · Memⱼ ≤ MEM_i ∀i
        """
        utilization = {}
        
        for task in tasks:
            if task.task_id not in assignments:
                continue
            
            node_ids = assignments[task.task_id]['nodes']
            for node_id in node_ids:
                host = topology.get_host(node_id)
                if host is None or not hasattr(host, 'memory_mb'):
                    continue
                
                if node_id not in utilization:
                    utilization[node_id] = 0.0
                utilization[node_id] += task.memory_requirement_mb
        
        is_feasible = True
        for node_id, load in utilization.items():
            host = topology.get_host(node_id)
            if host.memory_mb > 0:
                util_ratio = load / host.memory_mb
                if util_ratio > 1.0:
                    is_feasible = False
                utilization[node_id] = util_ratio
        
        return is_feasible, utilization
    
    @staticmethod
    def check_deadline_constraints(topology: FogCloudTopology, assignments: Dict,
                                  tasks: List[Task]) -> Tuple[bool, Dict[int, float]]:
        """
        Check deadline constraints.
        
        T^end_j ≤ Deadline_j ∀j
        """
        from fog_gwo_scheduler.models.system_model import NetworkModel
        
        deadline_violations = {}
        is_feasible = True
        
        for task in tasks:
            if task.task_id not in assignments:
                deadline_violations[task.task_id] = 0.0
                is_feasible = False
                continue
            
            assignment = assignments[task.task_id]
            node_ids = assignment['nodes']
            frequency_ghz = assignment.get('frequency', 2.0)
            
            if not node_ids:
                deadline_violations[task.task_id] = 0.0
                is_feasible = False
                continue
            
            # Use first replica's time
            node_id = node_ids[0]
            device_id = task.source_device_id
            host = topology.get_host(node_id)
            
            total_time = NetworkModel.task_total_time(task, device_id, host, frequency_ghz, topology)
            
            if total_time > task.deadline_s:
                is_feasible = False
            
            deadline_violations[task.task_id] = total_time / task.deadline_s  # Ratio
        
        return is_feasible, deadline_violations
    
    @staticmethod
    def check_all_constraints(topology: FogCloudTopology, assignments: Dict,
                             tasks: List[Task]) -> Dict[str, bool]:
        """
        Check all constraints.
        
        Returns:
            {'cpu_ok': bool, 'memory_ok': bool, 'deadline_ok': bool}
        """
        cpu_ok, _ = ConstraintHandler.check_cpu_capacity(topology, assignments, tasks)
        mem_ok, _ = ConstraintHandler.check_memory_capacity(topology, assignments, tasks)
        dl_ok, _ = ConstraintHandler.check_deadline_constraints(topology, assignments, tasks)
        
        return {
            'cpu': cpu_ok,
            'memory': mem_ok,
            'deadline': dl_ok,
            'all': cpu_ok and mem_ok and dl_ok,
        }

"""
Memory-Driven Grey Wolf Optimization (MD-GWO) Algorithm.

Implements SIREN's core optimization engine with memory mechanism.
Based on Eq. 19 in paper: X_k^(t+1) = (1/3)(X_α + X_β + X_δ) + η(t)(X_{k,pbest} - X_k^t)
"""

import numpy as np
from typing import List, Dict, Tuple, Callable, Optional
import logging

logger = logging.getLogger(__name__)


class Wolf:
    """
    Represents a wolf in the GWO swarm.
    
    Encodes a task schedule as continuous variables.
    """
    
    def __init__(self, position: np.ndarray, num_tasks: int, num_hosts: int):
        """
        Initialize a wolf.
        
        Args:
            position: Continuous position vector of size 3*num_tasks
            num_tasks: Number of tasks
            num_hosts: Number of hosts (fog + cloud)
        """
        self.position = position.copy()
        self.num_tasks = num_tasks
        self.num_hosts = num_hosts
        self.fitness = float('inf')
        self.pbest = position.copy()  # Personal best
        self.pbest_fitness = float('inf')
    
    def decode_position(self) -> Dict[int, Tuple[List[int], float]]:
        """
        Decode continuous position to discrete schedule.
        
        Each task gets 3 continuous dimensions:
        - x[3j] ∈ [0, num_hosts]: node ID
        - x[3j+1] ∈ [1, 3]: replication factor
        - x[3j+2] ∈ [0.4, 2.0]: CPU frequency (GHz)
        
        Returns:
            {task_id: ([node_ids], frequency_ghz)}
        """
        schedule = {}
        
        for j in range(self.num_tasks):
            idx_node = 3 * j
            idx_repl = 3 * j + 1
            idx_freq = 3 * j + 2
            
            # Decode node ID
            node_cont = self.position[idx_node] if idx_node < len(self.position) else 0.0
            node_id = int(np.round(node_cont)) % self.num_hosts
            
            # Decode replication factor
            repl_cont = self.position[idx_repl] if idx_repl < len(self.position) else 1.0
            replication = max(1, min(3, int(np.round(repl_cont))))  # r_max = 3
            
            # Decode frequency
            freq_cont = self.position[idx_freq] if idx_freq < len(self.position) else 2.0
            frequency_ghz = np.clip(freq_cont, 0.4, 2.0)  # Quantize to [0.4, 2.0]
            
            # Create replica list
            node_ids = [node_id]
            for r in range(1, replication):
                # Spread replicas across different nodes
                alt_node = (node_id + r) % self.num_hosts
                node_ids.append(alt_node)
            
            schedule[j] = (node_ids, frequency_ghz)
        
        return schedule
    
    def update_pbest(self, new_fitness: float):
        """Update personal best if fitness improves."""
        if new_fitness < self.pbest_fitness:
            self.pbest = self.position.copy()
            self.pbest_fitness = new_fitness


class MDGWO:
    """
    Memory-Driven Grey Wolf Optimizer.
    
    Combines:
    - Standard GWO for exploration/exploitation balance
    - Memory mechanism (personal best per wolf) for stability
    - Decaying memory coefficient η(t) for adaptation
    """
    
    def __init__(self, num_tasks: int, num_hosts: int, population_size: int = 100,
                 max_iterations: int = 200, memory_decay: str = 'linear'):
        """
        Initialize MDGWO.
        
        Args:
            num_tasks: Number of tasks to schedule
            num_hosts: Number of hosts (fog + cloud)
            population_size: Population size N_P
            max_iterations: Max iterations I
            memory_decay: 'linear', 'exponential', etc.
        """
        self.num_tasks = num_tasks
        self.num_hosts = num_hosts
        self.population_size = population_size
        self.max_iterations = max_iterations
        self.memory_decay = memory_decay
        
        self.wolves: List[Wolf] = []
        self.alpha: Optional[Wolf] = None
        self.beta: Optional[Wolf] = None
        self.delta: Optional[Wolf] = None
        
        self.iteration = 0
        self.best_fitness_history = []
    
    def initialize_population(self, random_seed: int = 42):
        """
        Initialize wolf population with random positions.
        
        Args:
            random_seed: Random seed for reproducibility
        """
        np.random.seed(random_seed)
        self.wolves = []
        
        for _ in range(self.population_size):
            # Initialize continuous position in [0, num_hosts] × [1, 3] × [0.4, 2.0]
            position = np.zeros(3 * self.num_tasks)
            
            for j in range(self.num_tasks):
                position[3*j] = np.random.uniform(0, self.num_hosts)  # Node ID
                position[3*j + 1] = np.random.uniform(1, 3)  # Replication factor
                position[3*j + 2] = np.random.uniform(0.4, 2.0)  # Frequency
            
            wolf = Wolf(position, self.num_tasks, self.num_hosts)
            self.wolves.append(wolf)
        
        # Initialize leaders
        self._update_leaders()
    
    def _update_leaders(self):
        """Update α, β, δ (best 3 wolves) based on fitness."""
        # Sort by fitness (ascending)
        sorted_wolves = sorted(self.wolves, key=lambda w: w.fitness)
        
        self.alpha = sorted_wolves[0]
        self.beta = sorted_wolves[1] if len(sorted_wolves) > 1 else sorted_wolves[0]
        self.delta = sorted_wolves[2] if len(sorted_wolves) > 2 else sorted_wolves[0]
    
    def _memory_coefficient(self, iteration: int) -> float:
        """
        Compute memory coefficient η(t) ∈ [0, 1].
        
        Controls shift from exploration to exploitation.
        
        Args:
            iteration: Current iteration (0 to max_iterations)
            
        Returns:
            Memory coefficient
        """
        if self.memory_decay == 'linear':
            # η(t) = 1 - t/I
            return 1.0 - (iteration / max(1, self.max_iterations))
        elif self.memory_decay == 'exponential':
            # η(t) = exp(-λt)
            lambda_decay = 2.0 / self.max_iterations
            return np.exp(-lambda_decay * iteration)
        else:
            return 1.0 - (iteration / max(1, self.max_iterations))
    
    def update_wolf(self, wolf: Wolf, iteration: int):
        """
        Update wolf position using MD-GWO rule (Eq. 19).
        
        X_k^(t+1) = (1/3)(X_α + X_β + X_δ) + η(t)(X_{k,pbest} - X_k^t)
        
        Args:
            wolf: Wolf object to update
            iteration: Current iteration
        """
        if self.alpha is None or self.beta is None or self.delta is None:
            return
        
        eta = self._memory_coefficient(iteration)
        
        # Social component: average of top 3
        social_position = (self.alpha.position + self.beta.position + self.delta.position) / 3.0
        
        # Memory component: personal best
        memory_component = eta * (wolf.pbest - wolf.position)
        
        # New position
        wolf.position = social_position + memory_component
        
        # Enforce bounds
        for i in range(len(wolf.position)):
            if i % 3 == 0:
                # Node ID: [0, num_hosts)
                wolf.position[i] = np.clip(wolf.position[i], 0, self.num_hosts - 1)
            elif i % 3 == 1:
                # Replication: [1, 3]
                wolf.position[i] = np.clip(wolf.position[i], 1, 3)
            else:
                # Frequency: [0.4, 2.0] GHz
                wolf.position[i] = np.clip(wolf.position[i], 0.4, 2.0)
    
    def optimize(self, fitness_function: Callable[[Wolf], float],
                 callback: Optional[Callable] = None) -> Wolf:
        """
        Run MD-GWO optimization.
        
        Args:
            fitness_function: Function that takes wolf and returns fitness (lower is better)
            callback: Optional callback(iteration, best_wolf, best_fitness) for logging
            
        Returns:
            Best wolf found
        """
        logger.info(f"Starting MD-GWO optimization: {self.max_iterations} iterations, "
                   f"{self.population_size} wolves, {self.num_tasks} tasks")
        
        self.best_fitness_history = []
        
        for iteration in range(self.max_iterations):
            # Evaluate all wolves
            for wolf in self.wolves:
                wolf.fitness = fitness_function(wolf)
                wolf.update_pbest(wolf.fitness)
            
            # Update leaders
            self._update_leaders()
            
            # Record best fitness
            best_fitness = self.alpha.fitness if self.alpha else float('inf')
            self.best_fitness_history.append(best_fitness)
            
            # Callback
            if callback and iteration % 10 == 0:
                callback(iteration, self.alpha, best_fitness)
            
            if iteration % 20 == 0:
                logger.info(f"Iteration {iteration}/{self.max_iterations}: "
                           f"Best fitness = {best_fitness:.2f}")
            
            # Update all wolves (except leaders)
            for wolf in self.wolves:
                if wolf is not self.alpha and wolf is not self.beta and wolf is not self.delta:
                    self.update_wolf(wolf, iteration)
            
            self.iteration = iteration
        
        logger.info(f"MD-GWO completed. Best fitness = {self.alpha.fitness:.2f}")
        return self.alpha
    
    def get_best_schedule(self) -> Dict[int, Tuple[List[int], float]]:
        """
        Decode best wolf's position to schedule.
        
        Returns:
            {task_id: ([node_ids], frequency_ghz)}
        """
        if self.alpha:
            return self.alpha.decode_position()
        return {}
    
    def get_convergence_curve(self) -> List[float]:
        """Return best fitness per iteration."""
        return self.best_fitness_history

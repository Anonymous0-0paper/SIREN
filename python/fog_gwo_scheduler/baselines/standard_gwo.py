"""
Standard Grey Wolf Optimizer (baseline, no memory mechanism).
"""

import numpy as np
from fog_gwo_scheduler.algorithms.mdgwo import MDGWO, Wolf


class StandardGWO(MDGWO):
    """
    Vanilla GWO without memory mechanism.
    
    Removes personal best updates, making updates purely social.
    """
    
    def update_wolf(self, wolf: Wolf, iteration: int):
        """
        Update wolf position using vanilla GWO (no memory).
        
        X_k^(t+1) = (1/3)(X_α + X_β + X_δ)
        
        Args:
            wolf: Wolf object to update
            iteration: Current iteration
        """
        if self.alpha is None or self.beta is None or self.delta is None:
            return
        
        # Social component only (no memory term)
        wolf.position = (self.alpha.position + self.beta.position + self.delta.position) / 3.0
        
        # Enforce bounds
        for i in range(len(wolf.position)):
            if i % 3 == 0:
                wolf.position[i] = np.clip(wolf.position[i], 0, self.num_hosts - 1)
            elif i % 3 == 1:
                wolf.position[i] = np.clip(wolf.position[i], 1, 3)
            else:
                wolf.position[i] = np.clip(wolf.position[i], 0.4, 2.0)

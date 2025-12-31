"""
Helper utilities and configuration loading.
"""

import yaml
from pathlib import Path
from typing import Dict, Any


def load_yaml_config(config_path: str) -> Dict[str, Any]:
    """
    Load YAML configuration file.
    
    Args:
        config_path: Path to YAML file
        
    Returns:
        Configuration dictionary
    """
    with open(config_path, 'r') as f:
        return yaml.safe_load(f)


def save_yaml_config(config: Dict, output_path: str):
    """Save configuration to YAML file."""
    with open(output_path, 'w') as f:
        yaml.dump(config, f)


def get_config_path(config_name: str) -> str:
    """Get path to a config file in configs/ directory."""
    repo_root = Path(__file__).parent.parent.parent.parent
    config_path = repo_root / 'configs' / f'{config_name}.yaml'
    if not config_path.exists():
        raise FileNotFoundError(f"Config file not found: {config_path}")
    return str(config_path)

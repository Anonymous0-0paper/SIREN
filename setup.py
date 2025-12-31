"""
Setup script for SIREN package.
"""

from setuptools import setup, find_packages

setup(
    name="fog-gwo-scheduler",
    version="1.0.0",
    description="SIREN: Multi-Objective Game-Theoretic Scheduler based on Memory-Driven Grey Wolf Optimization in Fog-Cloud Computing",
    author="Abolfazl Younesi, Mohsen Ansari, et al.",
    author_email="abolfazl.yunesi@uibk.ac.at",
    url="https://github.com/...",
    license="Apache 2.0",
    packages=find_packages(where="python"),
    package_dir={"": "python"},
    python_requires=">=3.9",
    install_requires=[
        "numpy==1.24.3",
        "scipy==1.11.0",
        "matplotlib==3.7.1",
        "pandas==2.0.3",
        "pyyaml==6.0",
        "pytest==7.4.0",
        "pytest-cov==4.1.0",
    ],
    entry_points={
        "console_scripts": [
            "siren=fog_gwo_scheduler.scripts.cli:main",
        ],
    },
    classifiers=[
        "Development Status :: 4 - Beta",
        "Intended Audience :: Science/Research",
        "License :: OSI Approved :: Apache Software License",
        "Programming Language :: Python :: 3.9",
        "Programming Language :: Python :: 3.10",
        "Programming Language :: Python :: 3.11",
        "Topic :: Scientific/Engineering",
        "Topic :: System :: Monitoring",
    ],
)

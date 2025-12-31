#!/bin/bash

# AWS initialization script for fog nodes
# Run on instance startup (user_data)

set -e

echo "=== SIREN Node Initialization ==="

# Update system
apt-get update && apt-get upgrade -y

# Install Python 3.11
apt-get install -y python3.11 python3.11-venv python3.11-dev python3-pip

# Install Git and utilities
apt-get install -y git curl awscli

# Clone repository (or pull from S3)
cd /home/ubuntu
git clone <REPO_URL> siren-fog-gwo || true
cd siren-fog-gwo

# Install Python dependencies
python3.11 -m pip install --upgrade pip
python3.11 -m pip install -r requirements.txt

# Install package
python3.11 -m pip install -e .

# Create working directories
mkdir -p /home/ubuntu/siren-fog-gwo/data/outputs
mkdir -p /home/ubuntu/siren-fog-gwo/results/{figures,tables}

# Configure AWS CLI for S3 access
aws configure set region us-east-1

echo "=== Initialization Complete ==="

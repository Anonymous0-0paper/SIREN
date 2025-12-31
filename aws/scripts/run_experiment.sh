#!/bin/bash

# Run SIREN experiments on AWS instances

NODES=${1:-15}
TASKS=${2:-3000}
SCENARIO=${3:-alibaba}
BUCKET=${4:-siren-results-bucket}

echo "Running SIREN experiment on $NODES fog nodes with $TASKS tasks"

# Run locally on each instance
cd /home/ubuntu/siren-fog-gwo/python/scripts

python cli.py \
    --mode full \
    --scenario $SCENARIO \
    --nodes $NODES \
    --tasks $TASKS \
    --seed 42 \
    --output ../../data/outputs

# Upload results to S3
aws s3 cp ../../data/outputs/ s3://$BUCKET/results/ --recursive
aws s3 cp ../../results/ s3://$BUCKET/results/ --recursive

echo "Experiment completed. Results uploaded to S3://$BUCKET"

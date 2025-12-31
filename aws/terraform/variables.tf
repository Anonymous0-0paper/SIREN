# Terraform variables

variable "aws_region" {
  description = "AWS region"
  type        = string
  default     = "us-east-1"
}

variable "num_fog_nodes" {
  description = "Number of fog node instances"
  type        = number
  default     = 15
}

variable "instance_types" {
  description = "EC2 instance types for fog nodes"
  type        = list(string)
  default = [
    "t4g.small",      # 2 vCPU, 2 GB RAM
    "t4g.medium",     # 2 vCPU, 4 GB RAM
    "t4g.large",      # 2 vCPU, 8 GB RAM
    "t3a.xlarge",     # 4 vCPU, 16 GB RAM
    "t3a.2xlarge"     # 8 vCPU, 32 GB RAM
  ]
}

variable "ssh_cidr" {
  description = "CIDR block for SSH access"
  type        = string
  default     = "0.0.0.0/0"  # Change to restrict access
}

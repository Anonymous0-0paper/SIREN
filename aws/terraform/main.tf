# AWS Infrastructure for SIREN Experiments
# Terraform configuration for fog-cloud testbed

terraform {
  required_version = ">= 1.0"
  required_providers {
    aws = {
      source  = "hashicorp/aws"
      version = "~> 5.0"
    }
  }
}

provider "aws" {
  region = var.aws_region
}

# VPC for isolated network
resource "aws_vpc" "siren_vpc" {
  cidr_block           = "10.0.0.0/16"
  enable_dns_hostnames = true

  tags = {
    Name = "siren-vpc"
  }
}

# Public subnet
resource "aws_subnet" "public" {
  vpc_id                  = aws_vpc.siren_vpc.id
  cidr_block              = "10.0.1.0/24"
  map_public_ip_on_launch = true

  tags = {
    Name = "siren-public"
  }
}

# Internet Gateway
resource "aws_internet_gateway" "siren_igw" {
  vpc_id = aws_vpc.siren_vpc.id

  tags = {
    Name = "siren-igw"
  }
}

# Route table
resource "aws_route_table" "public" {
  vpc_id = aws_vpc.siren_vpc.id

  route {
    cidr_block      = "0.0.0.0/0"
    gateway_id      = aws_internet_gateway.siren_igw.id
  }

  tags = {
    Name = "siren-public-rt"
  }
}

resource "aws_route_table_association" "public" {
  subnet_id      = aws_subnet.public.id
  route_table_id = aws_route_table.public.id
}

# Security group
resource "aws_security_group" "siren_sg" {
  name        = "siren-sg"
  description = "Security group for SIREN nodes"
  vpc_id      = aws_vpc.siren_vpc.id

  ingress {
    from_port   = 22
    to_port     = 22
    protocol    = "tcp"
    cidr_blocks = [var.ssh_cidr]
  }

  ingress {
    from_port   = 0
    to_port     = 65535
    protocol    = "tcp"
    self        = true
  }

  egress {
    from_port   = 0
    to_port     = 0
    protocol    = "-1"
    cidr_blocks = ["0.0.0.0/0"]
  }

  tags = {
    Name = "siren-sg"
  }
}

# S3 bucket for results
resource "aws_s3_bucket" "siren_results" {
  bucket = "siren-results-${data.aws_caller_identity.current.account_id}"

  tags = {
    Name = "siren-results"
  }
}

# Fog node instances
resource "aws_instance" "fog_nodes" {
  count                = var.num_fog_nodes
  ami                  = data.aws_ami.ubuntu.id
  instance_type        = var.instance_types[count.index % length(var.instance_types)]
  subnet_id            = aws_subnet.public.id
  security_groups      = [aws_security_group.siren_sg.id]
  iam_instance_profile = aws_iam_instance_profile.siren_profile.name

  user_data = base64encode(file("${path.module}/../scripts/init_node.sh"))

  tags = {
    Name = "fog-node-${count.index}"
    Role = "fog"
  }

  monitoring = true
}

# IAM role for EC2 instances
resource "aws_iam_role" "siren_role" {
  name = "siren-role"

  assume_role_policy = jsonencode({
    Version = "2012-10-17"
    Statement = [
      {
        Action = "sts:AssumeRole"
        Effect = "Allow"
        Principal = {
          Service = "ec2.amazonaws.com"
        }
      }
    ]
  })
}

# IAM policy for S3 access
resource "aws_iam_role_policy" "siren_policy" {
  name = "siren-policy"
  role = aws_iam_role.siren_role.id

  policy = jsonencode({
    Version = "2012-10-17"
    Statement = [
      {
        Effect = "Allow"
        Action = [
          "s3:GetObject",
          "s3:PutObject",
          "s3:ListBucket"
        ]
        Resource = [
          aws_s3_bucket.siren_results.arn,
          "${aws_s3_bucket.siren_results.arn}/*"
        ]
      }
    ]
  })
}

resource "aws_iam_instance_profile" "siren_profile" {
  name = "siren-profile"
  role = aws_iam_role.siren_role.name
}

# Data sources
data "aws_caller_identity" "current" {}

data "aws_ami" "ubuntu" {
  most_recent = true
  owners      = ["099720109477"]  # Canonical

  filter {
    name   = "name"
    values = ["ubuntu/images/hvm-ssd/ubuntu-focal-20.04-amd64-server-*"]
  }
}

# Outputs
output "fog_node_ips" {
  description = "Public IPs of fog nodes"
  value       = aws_instance.fog_nodes[*].public_ip
}

output "s3_bucket" {
  description = "S3 bucket for results"
  value       = aws_s3_bucket.siren_results.bucket
}

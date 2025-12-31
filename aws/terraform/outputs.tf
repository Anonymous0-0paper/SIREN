# Terraform outputs

output "fog_node_ips" {
  description = "Public IPs of fog nodes for SSH access"
  value       = aws_instance.fog_nodes[*].public_ip
}

output "fog_node_ids" {
  description = "EC2 instance IDs of fog nodes"
  value       = aws_instance.fog_nodes[*].id
}

output "s3_bucket_name" {
  description = "S3 bucket for experiment results"
  value       = aws_s3_bucket.siren_results.bucket
}

output "ssh_command" {
  description = "SSH command template for fog nodes"
  value       = "ssh -i <key.pem> ubuntu@${aws_instance.fog_nodes[0].public_ip}"
}

output "total_vCPUs" {
  description = "Total vCPUs across all instances"
  value       = "Approximately ${var.num_fog_nodes * 3} (depends on instance mix)"
}

output "estimated_monthly_cost" {
  description = "Estimated monthly cost (on-demand, us-east-1)"
  value       = "\$${var.num_fog_nodes * 0.05 * 730} (rough estimate)"
}

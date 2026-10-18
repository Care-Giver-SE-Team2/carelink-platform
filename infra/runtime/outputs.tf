output "cluster_name" {
  value = aws_eks_cluster.runtime.name
}

output "vpc_id" {
  value = aws_vpc.runtime.id
}

output "app_subnet_ids" {
  value = { for zone, subnet in aws_subnet.app : zone => subnet.id }
}

output "data_subnet_ids" {
  value = { for zone, subnet in aws_subnet.data : zone => subnet.id }
}

output "node_security_group_id" {
  description = "EKS's shared cluster/node SG; data resources must allow only required ports from this SG."
  value       = aws_eks_cluster.runtime.vpc_config[0].cluster_security_group_id
}

output "nat_gateway_count" {
  value = length(aws_nat_gateway.runtime)
}

output "kubeconfig_command" {
  description = "Run locally after apply; choose the carelink-runtime context explicitly for deployment."
  value       = "aws eks update-kubeconfig --name ${local.cluster_name} --region ap-southeast-1 --profile carelink --alias carelink-runtime"
}

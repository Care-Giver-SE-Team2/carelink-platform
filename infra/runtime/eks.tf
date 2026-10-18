resource "aws_iam_role" "cluster" {
  name = "CareLinkRuntimeCluster"
  assume_role_policy = jsonencode({
    Version = "2012-10-17"
    Statement = [{
      Effect    = "Allow"
      Principal = { Service = "eks.amazonaws.com" }
      Action    = "sts:AssumeRole"
    }]
  })
}

resource "aws_iam_role_policy_attachment" "cluster" {
  role       = aws_iam_role.cluster.name
  policy_arn = "arn:aws:iam::aws:policy/AmazonEKSClusterPolicy"
}

resource "aws_cloudwatch_log_group" "cluster" {
  name              = "/aws/eks/${local.cluster_name}/cluster"
  retention_in_days = 7
}

resource "aws_eks_cluster" "runtime" {
  name                      = local.cluster_name
  version                   = "1.35"
  role_arn                  = aws_iam_role.cluster.arn
  enabled_cluster_log_types = ["api", "audit", "authenticator"]

  access_config {
    authentication_mode                         = "API"
    bootstrap_cluster_creator_admin_permissions = false
  }

  upgrade_policy {
    support_type = "STANDARD"
  }

  vpc_config {
    subnet_ids              = [for subnet in aws_subnet.app : subnet.id]
    endpoint_private_access = true
    endpoint_public_access  = true
    public_access_cidrs     = [var.admin_ipv4_cidr]
  }

  depends_on = [aws_iam_role_policy_attachment.cluster, aws_cloudwatch_log_group.cluster]
}

resource "aws_eks_access_entry" "operator" {
  cluster_name  = aws_eks_cluster.runtime.name
  principal_arn = var.operator_principal_arn
  type          = "STANDARD"
}

resource "aws_eks_access_policy_association" "operator" {
  cluster_name  = aws_eks_cluster.runtime.name
  principal_arn = aws_eks_access_entry.operator.principal_arn
  policy_arn    = "arn:aws:eks::aws:cluster-access-policy/AmazonEKSClusterAdminPolicy"

  access_scope {
    type = "cluster"
  }
}

resource "aws_iam_role" "node" {
  name = "CareLinkRuntimeNode"
  assume_role_policy = jsonencode({
    Version = "2012-10-17"
    Statement = [{
      Effect    = "Allow"
      Principal = { Service = "ec2.amazonaws.com" }
      Action    = "sts:AssumeRole"
    }]
  })
}

# CNI still uses the node role during bootstrap. Application SDKs disable IMDS
# fallback; moving CNI to its own identity needs a separate bootstrap/recovery test.
resource "aws_iam_role_policy_attachment" "node" {
  for_each = toset([
    "AmazonEKSWorkerNodePolicy",
    "AmazonEC2ContainerRegistryPullOnly",
    "AmazonEKS_CNI_Policy"
  ])
  role       = aws_iam_role.node.name
  policy_arn = "arn:aws:iam::aws:policy/${each.key}"
}

resource "aws_launch_template" "node" {
  name_prefix = "${local.cluster_name}-"

  # Include possible surplus-credit charges in the estimate; avoid throttling cold starts.
  credit_specification {
    cpu_credits = "unlimited"
  }

  block_device_mappings {
    device_name = "/dev/xvda"
    ebs {
      volume_size           = 20
      volume_type           = "gp3"
      encrypted             = true
      delete_on_termination = true
    }
  }

  metadata_options {
    http_tokens                 = "required"
    http_put_response_hop_limit = 1
  }

  network_interfaces {
    device_index                = 0
    associate_public_ip_address = false
    security_groups             = [aws_eks_cluster.runtime.vpc_config[0].cluster_security_group_id]
  }

  tag_specifications {
    resource_type = "instance"
    tags          = { Project = "CareLink", Component = "Runtime" }
  }
}

# One group in each AZ guarantees one initial node per AZ. A single group spanning
# both subnets would not guarantee that placement when EC2 capacity is constrained.
resource "aws_eks_node_group" "runtime" {
  for_each = var.ha_worker_migration == "complete" ? {} : local.zones

  cluster_name    = aws_eks_cluster.runtime.name
  node_group_name = "${local.cluster_name}-${each.key}"
  node_role_arn   = aws_iam_role.node.arn
  subnet_ids      = [aws_subnet.app[each.key].id]
  ami_type        = "AL2023_x86_64_STANDARD"
  instance_types  = ["t3.medium"]
  capacity_type   = "ON_DEMAND"

  launch_template {
    id      = aws_launch_template.node.id
    version = aws_launch_template.node.latest_version
  }

  scaling_config {
    desired_size = 1
    min_size     = 1
    max_size     = 1
  }

  node_repair_config {
    enabled = true
  }

  update_config {
    max_unavailable = 1
  }

  depends_on = [aws_iam_role_policy_attachment.node, aws_route.app_outbound, aws_route_table_association.app, aws_vpc_endpoint.s3]
}

resource "aws_eks_node_group" "ha" {
  for_each = var.ha_worker_migration == "legacy" ? {} : local.zones

  cluster_name    = aws_eks_cluster.runtime.name
  node_group_name = "${local.cluster_name}-ha-${each.key}"
  node_role_arn   = aws_iam_role.node.arn
  subnet_ids      = [aws_subnet.app[each.key].id]
  ami_type        = "AL2023_x86_64_STANDARD"
  instance_types  = ["t3.large"]
  capacity_type   = "ON_DEMAND"

  launch_template {
    id      = aws_launch_template.node.id
    version = aws_launch_template.node.latest_version
  }

  scaling_config {
    desired_size = 1
    min_size     = 1
    max_size     = 1
  }

  node_repair_config {
    enabled = true
  }

  update_config {
    max_unavailable = 1
  }

  depends_on = [aws_iam_role_policy_attachment.node, aws_route.app_outbound, aws_route_table_association.app, aws_vpc_endpoint.s3]
}

resource "aws_eks_addon" "pod_identity" {
  cluster_name                = aws_eks_cluster.runtime.name
  addon_name                  = "eks-pod-identity-agent"
  resolve_conflicts_on_create = "OVERWRITE"
  resolve_conflicts_on_update = "PRESERVE"
  depends_on                  = [aws_eks_node_group.runtime, aws_eks_node_group.ha]
}

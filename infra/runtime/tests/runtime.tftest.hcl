# Mocked applies resolve IDs so route wiring can be checked without AWS calls.
mock_provider "aws" {
  mock_resource "aws_iam_role" {
    defaults = {
      arn = "arn:aws:iam::123456789012:role/CareLinkRuntimeMock"
    }
  }
  mock_resource "aws_launch_template" {
    defaults = {
      id             = "lt-0123456789abcdef0"
      latest_version = 1
    }
  }
}

variables {
  aws_account_id         = "123456789012"
  operator_principal_arn = "arn:aws:iam::123456789012:user/carelink-dev"
  admin_ipv4_cidr        = "203.0.113.10/32"
}

run "development_network_and_cluster" {
  command = apply

  assert {
    condition = (
      length(aws_subnet.public) == 2 && length(aws_subnet.app) == 2 && length(aws_subnet.data) == 2 &&
      length(toset(concat(
        [for subnet in aws_subnet.public : subnet.cidr_block],
        [for subnet in aws_subnet.app : subnet.cidr_block],
        [for subnet in aws_subnet.data : subnet.cidr_block]
      ))) == 6 &&
      alltrue([for subnet in aws_subnet.app : !subnet.map_public_ip_on_launch]) &&
      alltrue([for subnet in aws_subnet.data : !subnet.map_public_ip_on_launch])
    )
    error_message = "Use distinct public/app/data subnets across two AZs; app and data must not assign public IPs."
  }

  assert {
    condition = (
      length(aws_nat_gateway.runtime) == 1 &&
      alltrue([for route in aws_route.app_outbound : route.nat_gateway_id == aws_nat_gateway.runtime["a"].id]) &&
      alltrue([for table in aws_route_table.data : length(table.route) == 0]) &&
      aws_vpc_endpoint.s3.vpc_endpoint_type == "Gateway"
    )
    error_message = "Development uses one NAT; data has no added default route; S3 uses a gateway endpoint."
  }

  assert {
    condition = (
      length(aws_eks_node_group.runtime) == 2 &&
      alltrue([for zone, group in aws_eks_node_group.runtime :
        toset(group.subnet_ids) == toset([aws_subnet.app[zone].id]) &&
        toset(group.instance_types) == toset(["t3.medium"]) &&
        group.ami_type == "AL2023_x86_64_STANDARD" &&
        group.scaling_config[0].desired_size == 1 && group.scaling_config[0].max_size == 1
      ]) &&
      aws_launch_template.node.block_device_mappings[0].ebs[0].encrypted &&
      aws_launch_template.node.block_device_mappings[0].ebs[0].volume_size == 20 &&
      aws_launch_template.node.metadata_options[0].http_tokens == "required"
    )
    error_message = "One AMD64 t3.medium per AZ must match published images, with a 20 GiB encrypted disk and IMDSv2."
  }

  assert {
    condition = (
      aws_eks_cluster.runtime.access_config[0].authentication_mode == "API" &&
      !aws_eks_cluster.runtime.access_config[0].bootstrap_cluster_creator_admin_permissions &&
      aws_eks_cluster.runtime.vpc_config[0].endpoint_private_access &&
      toset(aws_eks_cluster.runtime.vpc_config[0].public_access_cidrs) == toset([var.admin_ipv4_cidr]) &&
      aws_eks_cluster.runtime.upgrade_policy[0].support_type == "STANDARD" &&
      aws_eks_access_entry.operator.principal_arn == var.operator_principal_arn
    )
    error_message = "Require explicit operator access, a private node endpoint, restricted public access and standard support."
  }
}

run "ha_network" {
  command = apply
  variables {
    high_availability = true
  }

  assert {
    condition = (
      length(aws_nat_gateway.runtime) == 2 &&
      alltrue([for zone, route in aws_route.app_outbound : route.nat_gateway_id == aws_nat_gateway.runtime[zone].id])
    )
    error_message = "HA must use a different NAT for each AZ's application route."
  }
}

run "return_to_development" {
  command = apply
  variables {
    high_availability = false
  }

  assert {
    condition = (
      output.nat_gateway_count == 1 &&
      aws_route.app_outbound["b"].nat_gateway_id == aws_nat_gateway.runtime["a"].id
    )
    error_message = "Returning to development must remove the second NAT and restore AZ b's route to NAT a."
  }
}

run "reject_public_api" {
  command = plan
  variables {
    admin_ipv4_cidr = "0.0.0.0/0"
  }
  expect_failures = [var.admin_ipv4_cidr]
}

run "reject_root_operator" {
  command = plan
  variables {
    operator_principal_arn = "arn:aws:iam::123456789012:root"
  }
  expect_failures = [var.operator_principal_arn]
}

run "reject_foreign_account_operator" {
  command = plan
  variables {
    operator_principal_arn = "arn:aws:iam::999999999999:user/carelink-dev"
  }
  expect_failures = [var.operator_principal_arn]
}

run "public_ingress_identity" {
  command = apply
  variables { enable_public_ingress = true }

  assert {
    condition = (
      aws_iam_role.load_balancer_controller[0].permissions_boundary == "arn:aws:iam::123456789012:policy/CareLinkLoadBalancerBoundary" &&
      jsondecode(aws_iam_role.load_balancer_controller[0].assume_role_policy).Statement[0].Principal.Service == "pods.eks.amazonaws.com" &&
      jsondecode(aws_iam_role.load_balancer_controller[0].assume_role_policy).Statement[0].Condition.StringEquals["aws:RequestTag/kubernetes-namespace"] == "kube-system" &&
      jsondecode(aws_iam_role.load_balancer_controller[0].assume_role_policy).Statement[0].Condition.StringEquals["aws:RequestTag/kubernetes-service-account"] == "aws-load-balancer-controller" &&
      jsondecode(aws_iam_role.load_balancer_controller[0].assume_role_policy).Statement[0].Condition.StringEquals["aws:RequestTag/eks-cluster-arn"] == aws_eks_cluster.runtime.arn
    )
    error_message = "Only the reviewed cluster/controller ServiceAccount may use the bounded ALB role."
  }
  assert {
    condition = (
      aws_eks_pod_identity_association.load_balancer_controller[0].namespace == "kube-system" &&
      aws_eks_pod_identity_association.load_balancer_controller[0].service_account == "aws-load-balancer-controller" &&
      aws_eks_pod_identity_association.load_balancer_controller[0].role_arn == aws_iam_role.load_balancer_controller[0].arn
    )
    error_message = "The association must match the trusted controller identity."
  }
  assert {
    condition     = length(regexall("(?i)(wafv2:|shield:|cognito-idp:|acm:|iam:CreateServiceLinkedRole)", aws_iam_role_policy.load_balancer_controller[0].policy)) == 0
    error_message = "The HTTP ALB controller must not manage unrelated identity, certificates or edge protection."
  }
}

run "completed_ha_nodes_and_network" {
  command = apply
  variables {
    high_availability   = true
    ha_worker_migration = "complete"
  }
  assert {
    condition = (
      length(aws_eks_node_group.runtime) == 0 && length(aws_eks_node_group.ha) == 2 &&
      alltrue([for zone, group in aws_eks_node_group.ha :
        toset(group.instance_types) == toset(["t3.large"]) &&
        toset(group.subnet_ids) == toset([aws_subnet.app[zone].id]) &&
        group.scaling_config[0].desired_size == 1 && group.scaling_config[0].max_size == 1
      ]) && length(aws_nat_gateway.runtime) == 2
    )
    error_message = "Completed HA uses a large node in each AZ and two NAT gateways, with no legacy node group; this is not node autoscaling."
  }
}

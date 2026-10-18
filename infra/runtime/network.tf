locals {
  cluster_name = "carelink-runtime"
  zones = {
    a = { name = "ap-southeast-1a", index = 0 }
    b = { name = "ap-southeast-1b", index = 1 }
  }
  nat_zones = var.high_availability ? local.zones : { a = local.zones.a }
}

resource "aws_vpc" "runtime" {
  cidr_block           = "10.42.0.0/16"
  enable_dns_support   = true
  enable_dns_hostnames = true
  tags                 = { Name = local.cluster_name }
}

resource "aws_internet_gateway" "runtime" {
  vpc_id = aws_vpc.runtime.id
}

resource "aws_subnet" "public" {
  for_each = local.zones

  vpc_id                  = aws_vpc.runtime.id
  availability_zone       = each.value.name
  cidr_block              = cidrsubnet(aws_vpc.runtime.cidr_block, 8, each.value.index)
  map_public_ip_on_launch = false
  tags = {
    Name                     = "${local.cluster_name}-public-${each.key}"
    "kubernetes.io/role/elb" = "1"
  }
}

resource "aws_subnet" "app" {
  for_each = local.zones

  vpc_id                  = aws_vpc.runtime.id
  availability_zone       = each.value.name
  cidr_block              = cidrsubnet(aws_vpc.runtime.cidr_block, 8, 10 + each.value.index)
  map_public_ip_on_launch = false
  tags = {
    Name                              = "${local.cluster_name}-app-${each.key}"
    "kubernetes.io/role/internal-elb" = "1"
  }
}

resource "aws_subnet" "data" {
  for_each = local.zones

  vpc_id                  = aws_vpc.runtime.id
  availability_zone       = each.value.name
  cidr_block              = cidrsubnet(aws_vpc.runtime.cidr_block, 8, 20 + each.value.index)
  map_public_ip_on_launch = false
  tags                    = { Name = "${local.cluster_name}-data-${each.key}" }
}

resource "aws_route_table" "public" {
  vpc_id = aws_vpc.runtime.id
}

resource "aws_route" "internet" {
  route_table_id         = aws_route_table.public.id
  destination_cidr_block = "0.0.0.0/0"
  gateway_id             = aws_internet_gateway.runtime.id
}

resource "aws_route_table_association" "public" {
  for_each       = local.zones
  subnet_id      = aws_subnet.public[each.key].id
  route_table_id = aws_route_table.public.id
}

resource "aws_eip" "nat" {
  for_each = local.nat_zones
  domain   = "vpc"
}

resource "aws_nat_gateway" "runtime" {
  for_each = local.nat_zones

  allocation_id = aws_eip.nat[each.key].id
  subnet_id     = aws_subnet.public[each.key].id
  depends_on    = [aws_internet_gateway.runtime]
}

resource "aws_route_table" "app" {
  for_each = local.zones
  vpc_id   = aws_vpc.runtime.id
}

resource "aws_route" "app_outbound" {
  for_each = local.zones

  route_table_id         = aws_route_table.app[each.key].id
  destination_cidr_block = "0.0.0.0/0"
  nat_gateway_id         = aws_nat_gateway.runtime[var.high_availability ? each.key : "a"].id
}

resource "aws_route_table_association" "app" {
  for_each       = local.zones
  subnet_id      = aws_subnet.app[each.key].id
  route_table_id = aws_route_table.app[each.key].id
}

# Data subnets have only the VPC-local route; no internet or NAT default route.
resource "aws_route_table" "data" {
  for_each = local.zones
  vpc_id   = aws_vpc.runtime.id
  route    = []
}

resource "aws_route_table_association" "data" {
  for_each       = local.zones
  subnet_id      = aws_subnet.data[each.key].id
  route_table_id = aws_route_table.data[each.key].id
}

# ECR image layers are stored in S3; a gateway endpoint avoids NAT processing for S3.
resource "aws_vpc_endpoint" "s3" {
  vpc_id            = aws_vpc.runtime.id
  service_name      = "com.amazonaws.ap-southeast-1.s3"
  vpc_endpoint_type = "Gateway"
  route_table_ids   = [for table in aws_route_table.app : table.id]
}

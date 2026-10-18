locals {
  data_count  = var.enable_data_services ? 1 : 0
  data_prefix = "carelink-${var.data_environment}"
  databases = var.enable_data_services ? {
    core         = { schema = "carelink", user = "carelink" }
    visit        = { schema = "carelink_visit", user = "visit" }
    report       = { schema = "carelink_report", user = "report" }
    notification = { schema = "carelink_notification", user = "notification" }
  } : {}
}

resource "aws_security_group" "data" {
  for_each = var.enable_data_services ? { mysql = 3306, redis = 6379 } : {}

  name        = "${local.data_prefix}-${each.key}"
  description = "${each.key} access from CareLink nodes only"
  vpc_id      = aws_vpc.runtime.id
  tags        = { Name = "${local.data_prefix}-${each.key}" }

  ingress {
    description     = "EKS node/pod connections"
    from_port       = each.value
    to_port         = each.value
    protocol        = "tcp"
    security_groups = [aws_eks_cluster.runtime.vpc_config[0].cluster_security_group_id]
  }

  egress {
    description = "Internal VPC traffic; data subnet routes have no internet default"
    from_port   = 0
    to_port     = 0
    protocol    = "-1"
    cidr_blocks = [aws_vpc.runtime.cidr_block]
  }
}

resource "aws_db_subnet_group" "data" {
  count      = local.data_count
  name       = "${local.data_prefix}-mysql"
  subnet_ids = [for subnet in aws_subnet.data : subnet.id]
}

resource "aws_db_parameter_group" "data" {
  count  = local.data_count
  name   = "${local.data_prefix}-mysql84"
  family = "mysql8.4"

  parameter {
    name         = "require_secure_transport"
    value        = "1"
    apply_method = "immediate"
  }
}

resource "aws_db_instance" "primary" {
  count = local.data_count

  lifecycle {
    prevent_destroy = true
  }

  identifier                 = "${local.data_prefix}-mysql"
  engine                     = "mysql"
  engine_version             = "8.4"
  instance_class             = "db.t4g.small"
  allocated_storage          = 20
  max_allocated_storage      = 0
  storage_type               = "gp3"
  storage_encrypted          = true
  snapshot_identifier        = var.database_snapshot_identifier
  db_name                    = var.database_snapshot_identifier == null ? "carelink" : null
  username                   = var.database_snapshot_identifier == null ? "carelink_admin" : null
  password_wo                = var.data_credentials.mysql_admin_password
  password_wo_version        = var.data_credentials_version
  db_subnet_group_name       = aws_db_subnet_group.data[0].name
  parameter_group_name       = aws_db_parameter_group.data[0].name
  vpc_security_group_ids     = [aws_security_group.data["mysql"].id]
  publicly_accessible        = false
  multi_az                   = var.high_availability
  apply_immediately          = var.ha_apply_immediately
  backup_retention_period    = 7
  ca_cert_identifier         = "rds-ca-rsa2048-g1"
  auto_minor_version_upgrade = true
  copy_tags_to_snapshot      = false
  deletion_protection        = var.database_deletion_protection
  skip_final_snapshot        = false
  final_snapshot_identifier  = "${local.data_prefix}-mysql-final-${var.data_run_id}"
}

# A read replica is not the Multi-AZ synchronous standby. Both are enabled for HA exercises.
resource "aws_db_instance" "reader" {
  count = var.enable_data_services && var.high_availability ? 1 : 0

  identifier                 = "${local.data_prefix}-mysql-reader"
  availability_zone          = local.zones.a.name
  replicate_source_db        = aws_db_instance.primary[0].arn
  instance_class             = "db.t4g.small"
  allocated_storage          = 20
  max_allocated_storage      = 0
  storage_type               = "gp3"
  storage_encrypted          = true
  db_subnet_group_name       = aws_db_subnet_group.data[0].name
  parameter_group_name       = aws_db_parameter_group.data[0].name
  vpc_security_group_ids     = [aws_security_group.data["mysql"].id]
  publicly_accessible        = false
  ca_cert_identifier         = "rds-ca-rsa2048-g1"
  auto_minor_version_upgrade = true
  # The primary's final snapshot retains the shared data; no duplicate reader snapshot.
  skip_final_snapshot = true
}

resource "aws_elasticache_subnet_group" "data" {
  count      = local.data_count
  name       = "${local.data_prefix}-redis"
  subnet_ids = [for subnet in aws_subnet.data : subnet.id]
}

resource "aws_elasticache_replication_group" "sessions" {
  count = local.data_count

  replication_group_id        = "${local.data_prefix}-redis"
  description                 = "CareLink ${var.data_environment} shared sessions"
  engine                      = "redis"
  engine_version              = "7.1"
  node_type                   = "cache.t4g.micro"
  parameter_group_name        = "default.redis7"
  port                        = 6379
  subnet_group_name           = aws_elasticache_subnet_group.data[0].name
  security_group_ids          = [aws_security_group.data["redis"].id]
  num_cache_clusters          = var.high_availability ? 2 : 1
  apply_immediately           = var.high_availability ? var.ha_apply_immediately : null
  preferred_cache_cluster_azs = var.high_availability ? [local.zones.a.name, local.zones.b.name] : [local.zones.a.name]
  automatic_failover_enabled  = var.high_availability
  multi_az_enabled            = var.high_availability
  at_rest_encryption_enabled  = true
  transit_encryption_enabled  = true
  transit_encryption_mode     = "required"
  auth_token_wo               = var.data_credentials.redis_auth_token
  auth_token_wo_version       = var.data_credentials_version
  auth_token_update_strategy  = "SET"
  snapshot_retention_limit    = 0
  auto_minor_version_upgrade  = true
}

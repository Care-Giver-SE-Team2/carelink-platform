mock_provider "aws" {
  mock_resource "aws_iam_role" {
    defaults = { arn = "arn:aws:iam::123456789012:role/CareLinkRuntimeMock" }
  }
  mock_resource "aws_launch_template" {
    defaults = { id = "lt-0123456789abcdef0", latest_version = 1 }
  }
  mock_resource "aws_db_instance" {
    defaults = {
      arn     = "arn:aws:rds:ap-southeast-1:123456789012:db:carelink-staging-mysql"
      address = "mysql.example.invalid"
    }
  }
  mock_resource "aws_elasticache_replication_group" {
    defaults = { primary_endpoint_address = "redis.example.invalid" }
  }
}

variables {
  aws_account_id         = "123456789012"
  operator_principal_arn = "arn:aws:iam::123456789012:user/carelink-dev"
  admin_ipv4_cidr        = "203.0.113.10/32"
  data_run_id            = "20261012-test"
  data_credentials = {
    mysql_admin_password = "AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA"
    redis_auth_token     = "BBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBB"
    service_passwords = {
      core         = "CCCCCCCCCCCCCCCCCCCCCCCCCCCCCCCC"
      visit        = "DDDDDDDDDDDDDDDDDDDDDDDDDDDDDDDD"
      report       = "EEEEEEEEEEEEEEEEEEEEEEEEEEEEEEEE"
      notification = "FFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFF"
    }
  }
}

run "data_disabled_by_default" {
  command = plan
  assert {
    condition = (
      length(aws_db_instance.primary) == 0 && length(aws_db_instance.reader) == 0 &&
      length(aws_elasticache_replication_group.sessions) == 0 &&
      length(aws_secretsmanager_secret.service_data) == 0
    )
    error_message = "An existing network-only run must not silently create data services."
  }
}

run "development_data_and_retention" {
  command = apply
  variables { enable_data_services = true }

  assert {
    condition = (
      aws_db_instance.primary[0].instance_class == "db.t4g.small" &&
      aws_db_instance.primary[0].allocated_storage == 20 &&
      aws_db_instance.primary[0].storage_encrypted && !aws_db_instance.primary[0].publicly_accessible &&
      !aws_db_instance.primary[0].multi_az && length(aws_db_instance.reader) == 0 &&
      aws_db_instance.primary[0].backup_retention_period == 7 &&
      !aws_db_instance.primary[0].skip_final_snapshot &&
      endswith(aws_db_instance.primary[0].final_snapshot_identifier, "20261012-test") &&
      aws_db_instance.primary[0].deletion_protection
    )
    error_message = "The development database must be small, private, encrypted and retained before deletion."
  }
  assert {
    condition = (
      aws_elasticache_replication_group.sessions[0].num_cache_clusters == 1 &&
      !aws_elasticache_replication_group.sessions[0].automatic_failover_enabled &&
      aws_elasticache_replication_group.sessions[0].at_rest_encryption_enabled &&
      aws_elasticache_replication_group.sessions[0].transit_encryption_mode == "required" &&
      toset(keys(aws_secretsmanager_secret.service_data)) == toset(["core", "visit", "report", "notification"]) &&
      length(aws_secretsmanager_secret_version.service_data) == 4
    )
    error_message = "Use one TLS Redis node and populate all four service credential Secrets."
  }
  assert {
    condition = (
      toset(aws_db_subnet_group.data[0].subnet_ids) == toset([for s in aws_subnet.data : s.id]) &&
      toset(aws_elasticache_subnet_group.data[0].subnet_ids) == toset([for s in aws_subnet.data : s.id]) &&
      alltrue([for name, sg in aws_security_group.data :
        length(sg.ingress) == 1 && alltrue([for rule in sg.ingress :
          toset(rule.security_groups) == toset([aws_eks_cluster.runtime.vpc_config[0].cluster_security_group_id]) &&
          try(length(rule.cidr_blocks), 0) == 0 && rule.from_port == (name == "mysql" ? 3306 : 6379)
        ])
      ])
    )
    error_message = "Place databases/cache in isolated data subnets and accept only node SG connections on their data ports."
  }
}

run "ha_data_redundancy" {
  command = apply
  variables {
    enable_data_services = true
    high_availability    = true
  }
  assert {
    condition = (
      aws_db_instance.primary[0].multi_az && length(aws_db_instance.reader) == 1 &&
      aws_db_instance.reader[0].replicate_source_db == aws_db_instance.primary[0].arn &&
      aws_elasticache_replication_group.sessions[0].num_cache_clusters == 2 &&
      aws_elasticache_replication_group.sessions[0].automatic_failover_enabled &&
      aws_elasticache_replication_group.sessions[0].multi_az_enabled &&
      toset(aws_elasticache_replication_group.sessions[0].preferred_cache_cluster_azs) == toset(["ap-southeast-1a", "ap-southeast-1b"])
    )
    error_message = "HA must enable a synchronous standby, separate read replica and two-AZ Redis failover."
  }
}

run "reject_enabled_data_without_credentials" {
  command = plan
  variables {
    enable_data_services = true
    data_credentials     = null
  }
  expect_failures = [var.data_credentials]
}

run "reject_unknown_data_environment" {
  command = plan
  variables { data_environment = "production" }
  expect_failures = [var.data_environment]
}

run "reject_overlong_rds_mysql_admin_password" {
  command = plan
  variables {
    enable_data_services = true
    data_credentials = {
      mysql_admin_password = "AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA"
      redis_auth_token     = "BBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBB"
      service_passwords = {
        core         = "CCCCCCCCCCCCCCCCCCCCCCCCCCCCCCCC"
        visit        = "DDDDDDDDDDDDDDDDDDDDDDDDDDDDDDDD"
        report       = "EEEEEEEEEEEEEEEEEEEEEEEEEEEEEEEE"
        notification = "FFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFF"
      }
    }
  }
  expect_failures = [var.data_credentials]
}

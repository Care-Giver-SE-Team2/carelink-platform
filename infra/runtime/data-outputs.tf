output "data_connections" {
  description = "Non-secret connection metadata. Credentials are in Secrets Manager, not outputs."
  value = var.enable_data_services ? {
    environment               = var.data_environment
    mysql_host                = aws_db_instance.primary[0].address
    mysql_port                = 3306
    mysql_reader_host         = try(aws_db_instance.reader[0].address, null)
    redis_host                = aws_elasticache_replication_group.sessions[0].primary_endpoint_address
    redis_port                = 6379
    database_admin_secret_arn = aws_secretsmanager_secret.database_admin[0].arn
    service_data_secret_arns  = { for service, secret in aws_secretsmanager_secret.service_data : service => secret.arn }
    service_databases         = local.databases
    final_snapshot_identifier = aws_db_instance.primary[0].final_snapshot_identifier
  } : null
}

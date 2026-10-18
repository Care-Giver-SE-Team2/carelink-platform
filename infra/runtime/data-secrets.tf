resource "aws_secretsmanager_secret" "database_admin" {
  count = local.data_count

  name                    = "carelink/${var.data_environment}/${var.data_run_id}/database-admin"
  description             = "Bootstrap only; never inject the RDS administrator into application pods"
  recovery_window_in_days = 7
}

resource "aws_secretsmanager_secret_version" "database_admin" {
  count = local.data_count

  secret_id                = aws_secretsmanager_secret.database_admin[0].id
  secret_string_wo         = jsonencode({ username = "carelink_admin", password = var.data_credentials.mysql_admin_password })
  secret_string_wo_version = var.data_credentials_version
}

resource "aws_secretsmanager_secret" "service_data" {
  for_each = local.databases

  name                    = "carelink/${var.data_environment}/${var.data_run_id}/${each.key}-data"
  description             = "${each.key} database and TLS Redis environment keys; message keys are a separate increment"
  recovery_window_in_days = 7
}

resource "aws_secretsmanager_secret_version" "service_data" {
  for_each = local.databases

  secret_id = aws_secretsmanager_secret.service_data[each.key].id
  secret_string_wo = jsonencode({
    DB_URL = join("", [
      "jdbc:mysql://${aws_db_instance.primary[0].address}:3306/${each.value.schema}",
      "?connectionTimeZone=Asia/Singapore&sslMode=VERIFY_IDENTITY",
      "&trustCertificateKeyStoreUrl=file:/etc/carelink/rds/rds-ca.p12",
      "&trustCertificateKeyStoreType=PKCS12&trustCertificateKeyStorePassword=changeit"
    ])
    DB_USER                       = each.value.user
    DB_PASSWORD                   = var.data_credentials.service_passwords[each.key]
    SPRING_DATA_REDIS_HOST        = aws_elasticache_replication_group.sessions[0].primary_endpoint_address
    SPRING_DATA_REDIS_PORT        = "6379"
    SPRING_DATA_REDIS_PASSWORD    = var.data_credentials.redis_auth_token
    SPRING_DATA_REDIS_SSL_ENABLED = "true"
  })
  secret_string_wo_version = var.data_credentials_version
}

terraform {
  required_version = ">= 1.11, < 2.0"
  required_providers {
    aws = { source = "hashicorp/aws", version = "~> 6.0" }
  }
  # A separate key: never apply the runtime root with guessed/default HA inputs.
  backend "s3" {}
}

variable "aws_account_id" {
  type = string
  validation {
    condition     = can(regex("^[0-9]{12}$", var.aws_account_id))
    error_message = "Expected a 12-digit account ID."
  }
}

provider "aws" {
  region              = "ap-southeast-1"
  allowed_account_ids = [var.aws_account_id]
  default_tags {
    tags = { Project = "CareLink", Component = "Runtime", ManagedBy = "Terraform" }
  }
}

locals {
  cluster = "carelink-runtime"
  topic   = "arn:aws:sns:ap-southeast-1:${var.aws_account_id}:carelink-staging-events"
  roles = {
    worker = { name = "report-worker", namespace = "staging", boundary = "CareLinkReportWorkerBoundary" }
    keda   = { name = "keda-operator", namespace = "keda", boundary = "CareLinkKedaQueueReadBoundary" }
  }
}

resource "aws_sqs_queue" "dlq" {
  name                      = "carelink-staging-report-worker-dlq"
  message_retention_seconds = 1209600
  sqs_managed_sse_enabled   = true
}

resource "aws_sqs_queue" "worker" {
  name                       = "carelink-staging-report-worker"
  message_retention_seconds  = 345600
  receive_wait_time_seconds  = 20
  visibility_timeout_seconds = 180
  sqs_managed_sse_enabled    = true
  redrive_policy             = jsonencode({ deadLetterTargetArn = aws_sqs_queue.dlq.arn, maxReceiveCount = 5 })
}

resource "aws_sqs_queue_redrive_allow_policy" "worker" {
  queue_url            = aws_sqs_queue.dlq.url
  redrive_allow_policy = jsonencode({ redrivePermission = "byQueue", sourceQueueArns = [aws_sqs_queue.worker.arn] })
}

resource "aws_sqs_queue_policy" "sns" {
  queue_url = aws_sqs_queue.worker.url
  policy = jsonencode({
    Version = "2012-10-17"
    Statement = [{
      Effect   = "Allow", Principal = { Service = "sns.amazonaws.com" }, Action = "sqs:SendMessage"
      Resource = aws_sqs_queue.worker.arn
      Condition = {
        ArnEquals    = { "aws:SourceArn" = local.topic }
        StringEquals = { "aws:SourceAccount" = var.aws_account_id }
      }
    }]
  })
}

resource "aws_sns_topic_subscription" "worker" {
  topic_arn            = local.topic
  protocol             = "sqs"
  endpoint             = aws_sqs_queue.worker.arn
  raw_message_delivery = true
  filter_policy_scope  = "MessageAttributes"
  filter_policy        = jsonencode({ type = ["ReportRequested"] })
  depends_on           = [aws_sqs_queue_policy.sns]
}

resource "aws_iam_role" "workload" {
  for_each             = local.roles
  name                 = "CareLinkRuntime-staging-${each.key == "worker" ? "report-worker" : "keda"}"
  permissions_boundary = "arn:aws:iam::${var.aws_account_id}:policy/${each.value.boundary}"
  assume_role_policy = jsonencode({
    Version = "2012-10-17"
    Statement = [{
      Effect = "Allow", Principal = { Service = "pods.eks.amazonaws.com" }, Action = ["sts:AssumeRole", "sts:TagSession"]
      Condition = { StringEquals = {
        "aws:RequestTag/eks-cluster-arn"            = "arn:aws:eks:ap-southeast-1:${var.aws_account_id}:cluster/${local.cluster}"
        "aws:RequestTag/kubernetes-namespace"       = each.value.namespace
        "aws:RequestTag/kubernetes-service-account" = each.value.name
      } }
    }]
  })
}

resource "aws_iam_role_policy" "queue" {
  for_each = local.roles
  name     = "CareLinkReportWorkerQueue"
  role     = aws_iam_role.workload[each.key].name
  policy = jsonencode({
    Version = "2012-10-17"
    Statement = [{
      Effect   = "Allow"
      Action   = each.key == "worker" ? ["sqs:ReceiveMessage", "sqs:DeleteMessage", "sqs:ChangeMessageVisibility"] : ["sqs:GetQueueAttributes"]
      Resource = aws_sqs_queue.worker.arn
    }]
  })
}

resource "aws_eks_pod_identity_association" "workload" {
  for_each             = local.roles
  cluster_name         = local.cluster
  namespace            = each.value.namespace
  service_account      = each.value.name
  role_arn             = aws_iam_role.workload[each.key].arn
  disable_session_tags = false
  depends_on           = [aws_iam_role_policy.queue]
}

output "connections" {
  value = {
    region    = "ap-southeast-1", cluster = local.cluster, namespace = "staging"
    topic_arn = local.topic, worker_queue_url = aws_sqs_queue.worker.url, dlq_url = aws_sqs_queue.dlq.url
    roles     = { for name, role in aws_iam_role.workload : name => role.arn }
  }
}

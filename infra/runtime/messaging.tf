variable "enable_messaging" {
  description = "Opt into SNS/SQS and four service Pod Identities; uses the same environment as data services."
  type        = bool
  default     = false
}

locals {
  message_services = var.enable_messaging ? toset(["core", "visit", "report", "notification"]) : toset([])
  event_filters = var.enable_messaging ? {
    notification = ["NotificationRequested"]
    report = [
      "IncidentRaised", "IncidentUpdated", "SpotCheckUpdated", "RosterChangeUpdated",
      "VisitScheduled", "VisitCaregiverChanged", "VisitCancelled", "VisitCheckedIn",
      "VisitCompleted", "VisitExceptionRaised", "VisitTaskRecorded", "VitalsRecorded",
      "ElderConfirmationSubmitted", "VisitClosed", "VisitEvidenceUpdated"
    ]
  } : {}
}

resource "aws_sns_topic" "events" {
  count = var.enable_messaging ? 1 : 0
  name  = "carelink-${var.data_environment}-events"
}

resource "aws_sqs_queue" "dead_letter" {
  for_each = local.message_services

  name                      = "carelink-${var.data_environment}-${each.key}-dlq"
  message_retention_seconds = 1209600
  sqs_managed_sse_enabled   = true
}

resource "aws_sqs_queue" "events" {
  for_each = local.message_services

  name                      = "carelink-${var.data_environment}-${each.key}"
  message_retention_seconds = 345600
  receive_wait_time_seconds = 20
  # The consumer receives up to ten messages and handles them sequentially.
  # Revisit this limit if a batch can take longer than five minutes.
  visibility_timeout_seconds = 300
  sqs_managed_sse_enabled    = true
  redrive_policy = jsonencode({
    deadLetterTargetArn = aws_sqs_queue.dead_letter[each.key].arn
    maxReceiveCount     = 5
  })
}

resource "aws_sqs_queue_redrive_allow_policy" "events" {
  for_each = local.message_services

  queue_url = aws_sqs_queue.dead_letter[each.key].url
  redrive_allow_policy = jsonencode({
    redrivePermission = "byQueue"
    sourceQueueArns   = [aws_sqs_queue.events[each.key].arn]
  })
}

resource "aws_sqs_queue_policy" "sns_delivery" {
  for_each = local.event_filters

  queue_url = aws_sqs_queue.events[each.key].url
  policy = jsonencode({
    Version = "2012-10-17"
    Statement = [{
      Sid       = "ReceiveFromCareLinkTopic"
      Effect    = "Allow"
      Principal = { Service = "sns.amazonaws.com" }
      Action    = "sqs:SendMessage"
      Resource  = aws_sqs_queue.events[each.key].arn
      Condition = {
        ArnEquals    = { "aws:SourceArn" = aws_sns_topic.events[0].arn }
        StringEquals = { "aws:SourceAccount" = var.aws_account_id }
      }
    }]
  })
}

resource "aws_sns_topic_subscription" "events" {
  for_each = local.event_filters

  topic_arn            = aws_sns_topic.events[0].arn
  protocol             = "sqs"
  endpoint             = aws_sqs_queue.events[each.key].arn
  raw_message_delivery = true
  filter_policy_scope  = "MessageAttributes"
  filter_policy        = jsonencode({ type = each.value })

  depends_on = [aws_sqs_queue_policy.sns_delivery]
}

resource "aws_iam_role" "service" {
  for_each = local.message_services

  name = "CareLinkRuntime-${var.data_environment}-${each.key}"
  # Created once by the administrator. The runtime operator cannot edit or remove it.
  permissions_boundary = "arn:aws:iam::${var.aws_account_id}:policy/CareLinkRuntimeWorkloadBoundary"
  assume_role_policy = jsonencode({
    Version = "2012-10-17"
    Statement = [{
      Effect    = "Allow"
      Principal = { Service = "pods.eks.amazonaws.com" }
      Action    = ["sts:AssumeRole", "sts:TagSession"]
      Condition = {
        StringEquals = {
          "aws:RequestTag/eks-cluster-arn"            = aws_eks_cluster.runtime.arn
          "aws:RequestTag/kubernetes-namespace"       = var.data_environment
          "aws:RequestTag/kubernetes-service-account" = each.key
        }
      }
    }]
  })
}

resource "aws_iam_role_policy" "service_events" {
  for_each = local.message_services

  name = "CareLinkEvents"
  role = aws_iam_role.service[each.key].name
  policy = jsonencode({
    Version = "2012-10-17"
    Statement = concat(
      contains(["core", "visit", "report"], each.key) ? [{
        Effect   = "Allow"
        Action   = ["sns:Publish"]
        Resource = aws_sns_topic.events[0].arn
      }] : [],
      contains(["report", "notification"], each.key) ? [{
        Effect   = "Allow"
        Action   = ["sqs:ReceiveMessage", "sqs:DeleteMessage"]
        Resource = aws_sqs_queue.events[each.key].arn
      }] : []
    )
  })
}

resource "aws_eks_pod_identity_association" "service" {
  for_each = local.message_services

  cluster_name         = aws_eks_cluster.runtime.name
  namespace            = var.data_environment
  service_account      = each.key
  role_arn             = aws_iam_role.service[each.key].arn
  disable_session_tags = false

  depends_on = [aws_iam_role_policy.service_events, aws_eks_addon.pod_identity]
}

output "message_connections" {
  description = "Non-secret event endpoints and service identities for deployment."
  value = var.enable_messaging ? {
    environment       = var.data_environment
    aws_account_id    = var.aws_account_id
    region            = "ap-southeast-1"
    cluster_name      = aws_eks_cluster.runtime.name
    topic_arn         = aws_sns_topic.events[0].arn
    queue_urls        = { for service, queue in aws_sqs_queue.events : service => queue.url }
    service_role_arns = { for service, role in aws_iam_role.service : service => role.arn }
  } : null
}

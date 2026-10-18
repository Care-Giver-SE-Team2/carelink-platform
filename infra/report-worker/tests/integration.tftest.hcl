mock_provider "aws" {
  mock_resource "aws_iam_role" {
    defaults = { arn = "arn:aws:iam::123456789012:role/MockWorker" }
  }
  mock_resource "aws_sqs_queue" {
    defaults = { arn = "arn:aws:sqs:ap-southeast-1:123456789012:mock", url = "https://sqs.ap-southeast-1.amazonaws.com/123456789012/mock" }
  }
}
variables { aws_account_id = "123456789012" }

run "dedicated_worker_resources_only" {
  command = apply
  assert {
    condition = (
      aws_sqs_queue.worker.name == "carelink-staging-report-worker" &&
      aws_sqs_queue.worker.visibility_timeout_seconds == 180 &&
      aws_sqs_queue.worker.sqs_managed_sse_enabled && aws_sqs_queue.dlq.sqs_managed_sse_enabled &&
      jsondecode(aws_sqs_queue.worker.redrive_policy).maxReceiveCount == 5 &&
      aws_sns_topic_subscription.worker.raw_message_delivery &&
      jsondecode(aws_sns_topic_subscription.worker.filter_policy).type == ["ReportRequested"]
    )
    error_message = "Keep Worker task routing isolated and encrypted."
  }
  assert {
    condition = (
      aws_eks_pod_identity_association.workload["worker"].service_account == "report-worker" &&
      aws_eks_pod_identity_association.workload["worker"].namespace == "staging" &&
      aws_eks_pod_identity_association.workload["keda"].namespace == "keda" &&
      jsondecode(aws_iam_role_policy.queue["keda"].policy).Statement[0].Action == ["sqs:GetQueueAttributes"] &&
      contains(jsondecode(aws_iam_role_policy.queue["worker"].policy).Statement[0].Action, "sqs:ChangeMessageVisibility")
    )
    error_message = "Separate KEDA attribute reads from Worker task consumption."
  }
}

#!/bin/sh
# Run by LocalStack when it is ready: docker-compose.yml mounts this file into
# /etc/localstack/init/ready.d/, and the events library's integration test (libs/events) copies it
# there, so a local run and the test use the same setup.
#
# The local stand-in for what the cloud environment creates with Terraform: one SNS topic for the
# events between services and, for each service, an SQS queue subscribed to it with raw message
# delivery, behind which a dead-letter queue takes a message after five failed receives.
#
# The subscriptions have no filter policy, so every service receives every event and ignores the
# types it has no handler for. In the cloud, each subscription filters on the "type" attribute, as
# the event catalogue lists.
set -eu

region=ap-southeast-1
account=000000000000

topic_arn=$(awslocal sns create-topic --region "$region" --name carelink-events --query TopicArn --output text)

for service in core visit report notification; do
  queue="carelink-$service"
  awslocal sqs create-queue --region "$region" --queue-name "$queue-dlq" >/dev/null
  redrive=$(printf '{"RedrivePolicy":"{\\"deadLetterTargetArn\\":\\"arn:aws:sqs:%s:%s:%s-dlq\\",\\"maxReceiveCount\\":\\"5\\"}"}' \
    "$region" "$account" "$queue")
  awslocal sqs create-queue --region "$region" --queue-name "$queue" --attributes "$redrive" >/dev/null
  awslocal sns subscribe --region "$region" --topic-arn "$topic_arn" --protocol sqs \
    --notification-endpoint "arn:aws:sqs:$region:$account:$queue" \
    --attributes RawMessageDelivery=true >/dev/null
done

echo "events: topic $topic_arn, queues carelink-{core,visit,report,notification} with dead-letter queues"

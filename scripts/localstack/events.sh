#!/bin/sh
# Run by LocalStack when it is ready: docker-compose.yml mounts this file into
# /etc/localstack/init/ready.d/, and the integration tests that need the queues copy it there, so a
# local run and the tests use the same setup.
#
# The local stand-in for what the cloud environment creates with Terraform: one SNS topic for the
# events between services and, for each service, an SQS queue behind which a dead-letter queue
# takes a message after five failed receives.
#
# A queue subscribes to the topic only for the events its service handles, with a filter policy on
# the message's "type" attribute (docs/platform/event-catalogue.md lists who handles what). A
# service that handles nothing yet, core and visit today, has a queue but no subscription. When a
# service gets its first handler, add its subscription here and in the Terraform.
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
done

# subscribe <service> <filter policy>: the service's queue takes the events the policy names, delivered raw
subscribe() {
  arn=$(awslocal sns subscribe --region "$region" --topic-arn "$topic_arn" --protocol sqs \
    --notification-endpoint "arn:aws:sqs:$region:$account:carelink-$1" \
    --attributes RawMessageDelivery=true --query SubscriptionArn --output text)
  awslocal sns set-subscription-attributes --region "$region" --subscription-arn "$arn" \
    --attribute-name FilterPolicy --attribute-value "$2"
}

subscribe notification '{"type":["NotificationRequested"]}'
subscribe report '{"type":["IncidentRaised","IncidentUpdated","SpotCheckUpdated","RosterChangeUpdated","VisitScheduled","VisitCaregiverChanged","VisitCancelled","VisitCheckedIn","VisitCompleted","VisitExceptionRaised","VisitTaskRecorded","VitalsRecorded","ElderConfirmationSubmitted","VisitClosed","VisitEvidenceUpdated"]}'

echo "events: topic $topic_arn; queues carelink-{core,visit,report,notification} with dead-letter queues; notification and report subscribed"

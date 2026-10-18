#!/usr/bin/env python3
"""Fail closed unless the first Worker Terraform plan only creates its eleven dedicated resources."""
import argparse
import json
from pathlib import Path

EXPECTED = {
    'aws_sqs_queue.dlq', 'aws_sqs_queue.worker', 'aws_sqs_queue_redrive_allow_policy.worker',
    'aws_sqs_queue_policy.sns', 'aws_sns_topic_subscription.worker',
    'aws_iam_role.workload["worker"]', 'aws_iam_role.workload["keda"]',
    'aws_iam_role_policy.queue["worker"]', 'aws_iam_role_policy.queue["keda"]',
    'aws_eks_pod_identity_association.workload["worker"]', 'aws_eks_pod_identity_association.workload["keda"]',
}


def validate(plan):
    if plan.get('errored') or plan.get('deferred_changes') or plan.get('resource_drift'):
        raise ValueError('The first Worker plan must be complete, without errors, deferrals or drift')
    resources = plan.get('resource_changes', [])
    if {r['address'] for r in resources} != EXPECTED:
        raise ValueError('Unexpected resources or an incomplete Worker plan')
    for resource in resources:
        if resource['change']['actions'] != ['create']:
            raise ValueError('Only initial creation is permitted; updates, imports and deletion require separate review')
        if resource.get('change', {}).get('importing'):
            raise ValueError('Existing resources must not be imported into this root')


if __name__ == '__main__':
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('plan_json', type=Path)
    args = parser.parse_args()
    try:
        validate(json.loads(args.plan_json.read_text()))
    except (ValueError, KeyError, OSError) as error:
        parser.exit(1, f'Worker plan rejected: {error}\n')
    print('Initial Worker plan scope passed: eleven dedicated creates; no existing infrastructure changes.')

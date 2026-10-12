#!/usr/bin/env python3
"""Inject service data Secrets and event endpoints without writing credential manifests to disk."""
import argparse
import base64
import json
import os
from pathlib import Path
import re
import subprocess
import sys

ROOT = Path(__file__).resolve().parents[1]
SERVICES = ("core", "visit", "report", "notification")
DATA_KEYS = {
    "DB_URL", "DB_USER", "DB_PASSWORD", "SPRING_DATA_REDIS_HOST",
    "SPRING_DATA_REDIS_PORT", "SPRING_DATA_REDIS_PASSWORD", "SPRING_DATA_REDIS_SSL_ENABLED",
}


def run_json(command, environment):
    result = subprocess.run(command, env=environment, capture_output=True, text=True)
    if result.returncode:
        # CLI errors can include request payloads. Do not expose them or credentials.
        raise ValueError(f"{command[0]} {command[1]} failed; no command output displayed")
    return json.loads(result.stdout)


def build_manifest(data, messages, environment, truststore, fetch_secret):
    if not data or not messages or data["environment"] != environment or messages["environment"] != environment:
        raise ValueError("Enable data and messaging for the selected environment first")
    account = messages["aws_account_id"]
    region = messages["region"]
    if not re.fullmatch(r"\d{12}", account) or region != "ap-southeast-1" or messages["cluster_name"] != "carelink-runtime":
        raise ValueError("Unexpected runtime account, region or cluster")
    topic = f"arn:aws:sns:{region}:{account}:carelink-{environment}-events"
    if messages["topic_arn"] != topic or set(data["service_data_secret_arns"]) != set(SERVICES):
        raise ValueError("Unexpected topic or service Secret mapping")
    if not truststore:
        raise ValueError("Prepare the public RDS truststore first")
    objects = [
        {"apiVersion": "v1", "kind": "Namespace", "metadata": {"name": environment}},
        {"apiVersion": "v1", "kind": "ConfigMap", "metadata": {"name": "rds-ca", "namespace": environment},
         "binaryData": {"rds-ca.p12": base64.b64encode(truststore).decode()}},
    ]
    # Fetch and validate all four values before making any Kubernetes mutation.
    for service in SERVICES:
        arn = data["service_data_secret_arns"][service]
        pattern = rf"arn:aws:secretsmanager:{region}:{account}:secret:carelink/{environment}/[a-z0-9-]+/{service}-data-[A-Za-z0-9]{{6}}"
        if not re.fullmatch(pattern, arn):
            raise ValueError(f"Unexpected data Secret ARN for {service}")
        response = fetch_secret(arn)
        if response["ARN"] != arn:
            raise ValueError(f"Unexpected Secrets Manager response for {service}")
        values = json.loads(response["SecretString"])
        if set(values) != DATA_KEYS or not all(isinstance(value, str) and value for value in values.values()):
            raise ValueError(f"Invalid data keys for {service}; values are not displayed")
        database = data["service_databases"][service]
        expected_url = f"jdbc:mysql://{data['mysql_host']}:3306/{database['schema']}?"
        if (values["DB_USER"] != database["user"] or not values["DB_URL"].startswith(expected_url)
                or "sslMode=VERIFY_IDENTITY" not in values["DB_URL"]
                or values["SPRING_DATA_REDIS_HOST"] != data["redis_host"]
                or values["SPRING_DATA_REDIS_PORT"] != "6379"
                or values["SPRING_DATA_REDIS_SSL_ENABLED"] != "true"):
            raise ValueError(f"Data endpoints or TLS settings do not match {service}")
        if service in ("core", "visit", "report"):
            values["CARELINK_EVENTS_TOPICARN"] = topic
        if service in ("report", "notification"):
            queue = f"https://sqs.{region}.amazonaws.com/{account}/carelink-{environment}-{service}"
            if messages["queue_urls"][service] != queue:
                raise ValueError(f"Unexpected queue URL for {service}")
            values["CARELINK_EVENTS_QUEUEURL"] = queue
        objects.append({
            "apiVersion": "v1", "kind": "Secret", "type": "Opaque",
            "metadata": {"name": f"{service}-runtime", "namespace": environment},
            "data": {key: base64.b64encode(value.encode()).decode() for key, value in values.items()},
        })
    return {"apiVersion": "v1", "kind": "List", "items": objects}


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("environment", choices=("staging", "demo"))
    parser.add_argument("--profile", required=True)
    parser.add_argument("--context", required=True)
    args = parser.parse_args()
    environment = dict(os.environ, AWS_PROFILE=args.profile, AWS_EC2_METADATA_DISABLED="true")
    aws = ["aws", "--profile", args.profile, "--region", "ap-southeast-1", "--output", "json", "--no-cli-pager"]
    terraform = ["terraform", f"-chdir={ROOT / 'infra/runtime'}", "output", "-json"]
    data = run_json(terraform + ["data_connections"], environment)
    messages = run_json(terraform + ["message_connections"], environment)
    if not messages:
        raise ValueError("Messaging has not been applied")
    identity = run_json(aws + ["sts", "get-caller-identity"], environment)
    if identity["Account"] != messages["aws_account_id"]:
        raise ValueError("AWS profile belongs to a different account")
    cluster = run_json(aws + ["eks", "describe-cluster", "--name", messages["cluster_name"]], environment)["cluster"]
    kube = ["kubectl", "--context", args.context]
    config = run_json(kube + ["config", "view", "--minify", "-o", "json"], environment)
    if (cluster["status"] != "ACTIVE" or len(config["clusters"]) != 1
            or config["clusters"][0]["cluster"]["server"] != cluster["endpoint"]):
        raise ValueError("Kubernetes context does not point to the active runtime cluster")
    truststore = (ROOT / "infra/.local/rds-ca/rds-ca.p12").read_bytes()
    manifest = build_manifest(data, messages, args.environment, truststore,
                              lambda arn: run_json(aws + ["secretsmanager", "get-secret-value", "--secret-id", arn], environment))
    # The namespace must exist before the server can dry-run namespaced objects.
    namespace_payload = json.dumps(manifest["items"].pop(0))
    payload = json.dumps(manifest)
    # Server-side apply avoids a last-applied annotation containing a second copy of each credential.
    # Admission-check the credential list first. Actual apply is not an atomic transaction.
    command = kube + ["apply", "--server-side", "--field-manager=carelink-runtime-config", "-f", "-"]
    namespace_result = subprocess.run(command, input=namespace_payload, text=True, env=environment, capture_output=True)
    if namespace_result.returncode:
        raise ValueError("Namespace preparation failed; output suppressed")
    for dry_run in (True, False):
        result = subprocess.run(command + (["--dry-run=server"] if dry_run else []),
                                input=payload, text=True, env=environment, capture_output=True)
        if result.returncode:
            raise ValueError("Kubernetes config validation/apply failed; output suppressed to protect credentials")
    print(f"Synced four runtime Secrets and public RDS CA ConfigMap into {args.environment}; no credentials displayed")


if __name__ == "__main__":
    try:
        main()
    except json.JSONDecodeError:
        print("Runtime config sync stopped: invalid JSON; values are not displayed", file=sys.stderr)
        sys.exit(1)
    except ValueError as error:
        print(f"Runtime config sync stopped: {error}", file=sys.stderr)
        sys.exit(1)
    except (KeyError, OSError) as error:
        # Never print arbitrary exception contents, which can include malformed secret values.
        print(f"Runtime config sync stopped ({type(error).__name__}); check profile, context, outputs and Secret configuration", file=sys.stderr)
        sys.exit(1)

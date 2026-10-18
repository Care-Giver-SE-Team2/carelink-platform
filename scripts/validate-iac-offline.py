#!/usr/bin/env python3
"""Validate only allowlisted Terraform source in a disposable, credential-free workspace."""
import os
from pathlib import Path
import shutil
import subprocess
import tempfile

ROOT = Path(__file__).resolve().parents[1]
ROOTS = ["infra", "infra/bootstrap", "infra/runtime", "infra/public-site"]


def main():
    # Exclude caller credentials, real TF_VAR inputs and TF_CLI_ARGS injection.
    env = {k: v for k, v in os.environ.items() if not k.startswith(("AWS_", "TF_"))}
    env.update(TF_IN_AUTOMATION="1", TF_INPUT="0", AWS_EC2_METADATA_DISABLED="true")
    with tempfile.TemporaryDirectory(prefix="carelink-iac-offline-") as temporary:
        workspace = Path(temporary)
        for source in (ROOT / "infra").rglob("*"):
            relative = source.relative_to(ROOT)
            if any(part.startswith(".") for part in relative.parts) and source.name != ".terraform.lock.hcl":
                continue
            if not source.is_file() or not (source.suffix == ".tf" or source.name.endswith(".tftest.hcl") or
                                            source.name in {".terraform.lock.hcl", "controller-policy.json"}):
                continue
            destination = workspace / relative
            destination.parent.mkdir(parents=True, exist_ok=True)
            shutil.copyfile(source, destination)
        destination = workspace / "deploy/cloudfront/spa.js"
        destination.parent.mkdir(parents=True, exist_ok=True)
        shutil.copyfile(ROOT / "deploy/cloudfront/spa.js", destination)
        for directory in ROOTS:
            print(f"Checking {directory} with mocked providers; no remote backend", flush=True)
            for command in [["fmt", "-check"], ["init", "-backend=false", "-input=false", "-lockfile=readonly"],
                            ["validate"], ["test"]]:
                subprocess.run(["terraform", *command], cwd=workspace / directory, env=env, check=True)


if __name__ == "__main__":
    main()

"""Validate both hand-written specifications and every local reference. No file writes."""
import sys
from pathlib import Path
import yaml
from openapi_spec_validator import validate

root = Path(__file__).resolve().parents[1]
for name in ("openapi.yaml", "openapi-draft.yaml"):
    path = root / "docs" / "api" / name
    spec = yaml.safe_load(path.read_text(encoding="utf-8"))
    validate(spec, base_uri=path.as_uri())
    references = []

    def visit(node):
        if isinstance(node, dict):
            if "$ref" in node:
                reference = node["$ref"]
                if not reference.startswith("#/"):
                    raise ValueError(f"Unexpected external reference: {reference}")
                resolved = spec
                for part in reference[2:].split("/"):
                    resolved = resolved[part.replace("~1", "/").replace("~0", "~")]
                references.append(reference)
            for child in node.values():
                visit(child)
        elif isinstance(node, list):
            for child in node:
                visit(child)

    visit(spec)
    print(f"{name}: valid OpenAPI {spec['openapi']}, {len(spec['paths'])} paths, {len(references)} resolved local references")

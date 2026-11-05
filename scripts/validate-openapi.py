"""Validate both hand-written specifications and every local reference. No file writes."""
import sys
from pathlib import Path
import yaml
from openapi_spec_validator import validate

root = Path(__file__).resolve().parents[1]
api_root = (root / "docs" / "api").resolve()
documents = {}

def document(path):
    path = path.resolve()
    if not path.is_relative_to(api_root):
        raise ValueError(f"Reference outside API directory: {path}")
    if path not in documents:
        documents[path] = yaml.safe_load(path.read_text(encoding="utf-8"))
    return documents[path]

for name in ("openapi.yaml", "openapi-draft.yaml"):
    path = root / "docs" / "api" / name
    spec = document(path)
    references = []
    visited = set()

    def visit(node, source):
        if isinstance(node, dict):
            if "$ref" in node:
                reference = node["$ref"]
                filename, separator, pointer = reference.partition("#")
                if not separator or not pointer.startswith("/") or ":" in filename or filename.startswith(("/", "\\")):
                    raise ValueError(f"Unexpected non-local reference: {reference}")
                target = (source.parent / filename).resolve() if filename else source
                resolved = document(target)
                for part in pointer[1:].split("/"):
                    resolved = resolved[part.replace("~1", "/").replace("~0", "~")]
                references.append(reference)
                key = (target, pointer)
                if key not in visited:
                    visited.add(key)
                    visit(resolved, target)
            for child in node.values():
                visit(child, source)
        elif isinstance(node, list):
            for child in node:
                visit(child, source)

    # Reject network/path escapes before the validator resolves any references.
    visit(spec, path.resolve())
    validate(spec, base_uri=path.as_uri())
    print(f"{name}: valid OpenAPI {spec['openapi']}, {len(spec['paths'])} paths, {len(references)} resolved local references")

#!/usr/bin/env python3
"""Checks the Safanoria JSON Schemas.

- Both schemas are valid JSON Schema 2020-12.
- Every file in examples/<schema>/valid/ passes.
- Every file in examples/<schema>/invalid/ fails with the error its first line declares:
  `# expect: <keyword> <json-pointer>`, e.g. `# expect: pattern /id` (`/` is the root).
- This repository's own safanoria.yaml and ticket frontmatter pass.

YAML is loaded without timestamp conversion, so `2026-10-01` stays a string, as in the CLI
(kaml) and in YAML 1.2. PyYAML follows YAML 1.1 and would otherwise turn it into a date.

Usage: python3 schema/check.py    (needs PyYAML and jsonschema)
"""
import json
import pathlib
import sys

import jsonschema
import yaml

SCHEMA_DIR = pathlib.Path(__file__).resolve().parent
REPO = SCHEMA_DIR.parent


class Loader(yaml.SafeLoader):
    pass


Loader.yaml_implicit_resolvers = {
    first: [r for r in resolvers if r[0] != "tag:yaml.org,2002:timestamp"]
    for first, resolvers in yaml.SafeLoader.yaml_implicit_resolvers.items()
}


def load_yaml(text):
    return yaml.load(text, Loader)


def frontmatter(text):
    lines = text.split("\n")
    if lines[0] != "---" or "---" not in lines[1:]:
        return None
    return "\n".join(lines[1 : lines.index("---", 1)])


def validator(name):
    schema = json.loads((SCHEMA_DIR / f"{name}.schema.json").read_text())
    jsonschema.Draft202012Validator.check_schema(schema)
    return jsonschema.Draft202012Validator(schema)


def errors(v, data):
    return [("/" + "/".join(str(p) for p in e.absolute_path), e.validator, e.message) for e in v.iter_errors(data)]


def main():
    failures = []
    checked = 0
    for name in ["ticket", "safanoria"]:
        v = validator(name)
        examples = SCHEMA_DIR / "examples" / name
        for f in sorted((examples / "valid").glob("*.yaml")):
            checked += 1
            for path, keyword, message in errors(v, load_yaml(f.read_text())):
                failures.append(f"{f.relative_to(REPO)}: unexpected {keyword} at {path}: {message}")
        for f in sorted((examples / "invalid").glob("*.yaml")):
            checked += 1
            text = f.read_text()
            first = text.split("\n", 1)[0]
            if not first.startswith("# expect: "):
                failures.append(f"{f.relative_to(REPO)}: first line must be '# expect: <keyword> <pointer>'")
                continue
            keyword, pointer = first[len("# expect: "):].split()
            found = errors(v, load_yaml(text))
            if not any(k == keyword and p == pointer for p, k, _ in found):
                got = "; ".join(f"{k} at {p}" for p, k, _ in found) or "no errors"
                failures.append(f"{f.relative_to(REPO)}: expected {keyword} at {pointer}, got {got}")

    config = validator("safanoria")
    checked += 1
    for path, keyword, message in errors(config, load_yaml((REPO / "safanoria.yaml").read_text())):
        failures.append(f"safanoria.yaml: {keyword} at {path}: {message}")
    ticket = validator("ticket")
    for f in sorted((REPO / "tickets").glob("*.md")):
        fm = frontmatter(f.read_text())
        if fm is None:
            continue
        checked += 1
        for path, keyword, message in errors(ticket, load_yaml(fm)):
            failures.append(f"{f.relative_to(REPO)}: {keyword} at {path}: {message}")

    for failure in failures:
        print(failure)
    print(f"{checked} files checked, {len(failures)} failures")
    return 1 if failures else 0


if __name__ == "__main__":
    sys.exit(main())

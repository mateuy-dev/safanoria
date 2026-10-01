---
id: v1-tooling-validate
type: feature
title: "`safanoria validate`: every SPEC §12 check"
status: backlog
priority: high
size: M
created: 2026-10-01
updated: 2026-10-01
parent: v1-tooling
blockedBy: [v1-tooling-schema, v1-tooling-cli-core]
---

## Objective

`safanoria validate` reports every rule listed in SPEC §12, with file and line, so tickets
written by hand or by agents stay inside the format.

## Acceptance Criteria

- [ ] One test fixture per §12 rule, each reported with file and line
- [ ] Exit code 0 when valid, non-zero on errors
- [ ] `--format json` for machine-readable output (CI, editors)
- [ ] Can validate only given files (for pre-commit), still checking cross-file references
      against all tickets

## Plan

## Design

From `v1-tooling-native-spike`:

- Per-file rules come from the JSON Schema (`v1-tooling-schema`), checked at runtime with
  OptimumCode `json-schema-validator` 0.5.5: convert the kaml node tree to `JsonElement`
  (scalars typed as in YAML 1.2: integers, booleans, else strings), validate, and map each
  error's JSON pointer back to a kaml node's line (the map's line when the property is missing).
  Cross-file rules are Kotlin.
- Linux cost, accepted by the user: the validator's `com.doist.x:normalize` links
  `-lunistring`. The build needs a `libunistring.so` symlink on the linker path
  (`linkerOpts("-L…")`), and the binary needs `libunistring.so.5` at runtime (Ubuntu 24.04+).
  If older distros must be supported, replace the validator with Kotlin rules (the schema stays
  for editors).
- Staged-files mode still needs every ticket's frontmatter (references), not every body.

## Work Log

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

## Work Log

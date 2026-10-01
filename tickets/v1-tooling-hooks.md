---
id: v1-tooling-hooks
type: feature
title: Pre-commit hook and CI example running validate
status: backlog
priority: medium
size: S
created: 2026-10-01
updated: 2026-10-01
parent: v1-tooling
blockedBy: [v1-tooling-validate, v1-tooling-install]
---

## Objective

A project can run `safanoria validate` automatically before each commit and in CI, so invalid
tickets do not reach the main branch. Both call the native `safanoria` binary; CI gets it with
the install script from `v1-tooling-install`.

## Acceptance Criteria

- [ ] A pre-commit hook that validates staged tickets, installable with one command or snippet
- [ ] A GitHub Actions example workflow that installs the binary and runs `validate`, documented
      in README.md
- [ ] This repository uses both

## Plan

## Work Log

- **2026-10-01** · plan · Blocked also by `install`: CI needs the published binary.

---
id: v1-tooling-spec-decisions
type: feature
title: Decide Safanoria versioning, attachments and per-type templates
status: backlog
priority: medium
size: S
created: 2026-10-01
updated: 2026-10-01
parent: v1-tooling
---

## Objective

Open points left in SPEC v1, to decide and write into SPEC.md:

- **Versioning of Safanoria itself**: spec version (`safanoria: 1`) versus tool/skill version,
  and where the tool version lives, so this repository's `safanoria` component gets a `version`
  source instead of `external: true`. `v1-tooling-install` needs this to know what it installs.
- **Attachments**: naming under `attachments/<id>/`, size limits (git history is permanent),
  how they are referenced from a ticket and shown by apps.
- **Templates per type**: whether `research` (questions as criteria) or `bug` (steps to
  reproduce) need their own template, and how a project declares them.

## Acceptance Criteria

- [ ] Each point decided, with the rejected alternatives in this ticket
- [ ] SPEC.md updated; spec version bumped only if SPEC §13 requires it

## Plan

## Work Log

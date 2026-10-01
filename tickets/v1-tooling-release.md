---
id: v1-tooling-release
type: feature
title: "`safanoria release`: stamp resolvedIn, also for external components"
status: backlog
priority: high
size: M
created: 2026-10-01
updated: 2026-10-01
parent: v1-tooling
blockedBy: [v1-tooling-cli-core]
---

## Objective

`safanoria release <component> [<version>]` stamps `resolvedIn` as in SPEC §9, so a project's
release process can call it instead of editing tickets by hand. Without a version, it reads it
from the component's `version` source.

Also decide and specify the open point: components released from another repository
(`external: true`, e.g. VacApp's Rails server). Who runs `release`, and how it reaches the
ticket repository (e.g. CI job of the other repository opening a commit/PR here, or a manual
command with the version given).

## Acceptance Criteria

- [ ] Stamps every `done` ticket with the component in `area` and no `resolvedIn.<c>`, adding
      the `release · <c> <v>` Work Log entry; nothing else in the files changes
- [ ] Refuses to run off `mainBranch` (overridable), and on a malformed version
- [ ] Reads the version from `{ file, property }` and `{ file, regex }` sources
- [ ] `--dry-run` lists what would be stamped
- [ ] The external-component flow is decided and written into SPEC §9

## Plan

## Work Log

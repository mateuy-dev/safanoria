---
id: v1-tooling-new
type: feature
title: "`safanoria new`: create a ticket from a title"
status: backlog
priority: medium
size: S
created: 2026-10-01
updated: 2026-10-01
parent: v1-tooling
blockedBy: [v1-tooling-cli-core]
---

## Objective

`safanoria new "<title>"` does the mechanical part of SPEC §11 Create: suggests an id from the
title, checks it against existing tickets and branches (SPEC §3), and writes the ticket from the
project's template with `status: backlog` and today's dates. Agents and humans then fill the
Objective.

## Acceptance Criteria

- [ ] Suggests a valid id from the title; `--id` overrides it
- [ ] Refuses an id that exists as a ticket; warns when a branch has that name
- [ ] `--parent <id>` sets `parent`, suggests a child id, and adds the child item to the
      parent's Plan
- [ ] `--type`, `--priority`, `--size` options; uses `<dir>/_TEMPLATE.md` when present

## Plan

## Work Log

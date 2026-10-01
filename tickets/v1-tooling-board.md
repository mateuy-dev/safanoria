---
id: v1-tooling-board
type: feature
title: "`safanoria list` and `safanoria board`"
status: backlog
priority: medium
size: S
created: 2026-10-01
updated: 2026-10-01
parent: v1-tooling
blockedBy: [v1-tooling-cli-core]
---

## Objective

Read-only views over the tickets:

- `list`: one line per ticket, filterable (status, type, area, parent), for the terminal and
  for agents.
- `board`: a markdown board by status; parents with their children and progress; blocked
  tickets marked with what blocks them. Written to stdout or a file, so it can be committed or
  published.

## Acceptance Criteria

- [ ] `list` with filters by status, type, area and parent
- [ ] `board` groups by status, nests children under parents with `done/total`, and marks
      tickets blocked by a ticket that is not `done`
- [ ] Reverse relations are derived (SPEC §8): children of a parent, tickets a ticket blocks

## Plan

## Work Log

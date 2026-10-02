---
id: v1-tooling-board
type: feature
title: "`safanoria list` and `safanoria board`"
status: in-progress
priority: medium
size: S
created: 2026-10-01
updated: 2026-10-02
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

Both views are built in `core` (the GUI shows the same), from one `TicketGraph` that derives
the reverse relations; the CLI formats them. Output is deterministic (no timestamps, stable
order), so a committed board only changes when tickets do.

- [ ] `TicketGraph` in `core`: children of a parent, the parent of a child, tickets each one
      blocks, open blockers (`blockedBy` not `done`), a parent's progress (Plan items checked /
      total, children and own steps, as §8.1 defines "done"). Order: status (in-progress,
      review, ready, backlog, done, wontfix), then priority (urgent first), then id.
- [ ] The `list` command: one line per ticket (`id  status  priority  type  size  title`, markers for
      parent, children progress and blocked), filters `--status` (comma list), `--type`,
      `--area`, `--parent`, `--blocked`; `--format json` with every field plus the derived
      relations (`children`, `blocks`, `openBlockers`, `progress`), for agents.
- [ ] The `board` command: markdown, a section per status with counts; tickets without a parent at the top
      level, each parent followed by its children as a checklist (checked when `done`/`wontfix`)
      with `done/total`; blocked tickets marked `blocked by <ids>`; `done` and `wontfix`
      compact (id and title). Ids link to the ticket files, relative to where the board is
      written (`--output FILE`, default stdout, links relative to the root), so it reads well on
      GitHub. Golden-file test on the `valid` fixture, on JVM and native.
- [ ] README: `list` and `board` usage; this repository's board generated as an example.

## Work Log
- **2026-10-02** · status · Started. Branch `v1-tooling-board` from `v1-tooling`, worktree
  `../safanoria--v1-tooling-board`.
- **2026-10-02** · plan · One `TicketGraph` in `core` for both views. Deterministic board
  output, ids linked to ticket files relative to the output file, so it can be committed.
- **2026-10-02** · plan · `validate` flagged two Plan items starting with `` `list` `` and
  `` `board` ``: §7.5 reads a backticked first word as a child id. Reworded ("The `list`
  command…").

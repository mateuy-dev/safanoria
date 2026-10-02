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

- [x] `list` with filters by status, type, area and parent
- [x] `board` groups by status, nests children under parents with `done/total`, and marks
      tickets blocked by a ticket that is not `done`
- [x] Reverse relations are derived (SPEC §8): children of a parent, tickets a ticket blocks

## Plan

Both views are built in `core` (the GUI shows the same), from one `TicketGraph` that derives
the reverse relations; the CLI formats them. Output is deterministic (no timestamps, stable
order), so a committed board only changes when tickets do.

- [x] `TicketGraph` in `core`: children of a parent, the parent of a child, tickets each one
      blocks, open blockers (`blockedBy` not `done`), a parent's progress (Plan items checked /
      total, children and own steps, as §8.1 defines "done"). Order: status (in-progress,
      review, ready, backlog, done, wontfix), then priority (urgent first), then id.
- [x] The `list` command: one line per ticket (`id  status  priority  type  size  title`, markers for
      parent, children progress and blocked), filters `--status` (comma list), `--type`,
      `--area`, `--parent`, `--blocked`; `--format json` with every field plus the derived
      relations (`children`, `blocks`, `openBlockers`, `progress`), for agents.
- [x] The `board` command: markdown, a section per status with counts; tickets without a parent at the top
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
- **2026-10-02** · step 1 · `TicketGraph(tickets)`, also `Repository.graph`: `parent`,
  `children` (Plan order, unlisted children last by id), `blocks`, `openBlockers` (ids; a
  missing ticket counts as open, `validate` reports it), `progress` (null for an empty Plan;
  given for any ticket, formatters show it for parents). Unknown status or priority sorts last.
  3 tests, JVM and linuxX64.
- **2026-10-02** · step 2 · `safanoria list`; filtering in `core` (`TicketFilter`) for apps.
  `--status` and `--type` take comma lists checked by Clikt (`choice` before `split`, so a bad
  value gets `list`'s usage, exit 2); `--area` too. Markers after the title: `[done/total]` on
  parents, `parent <id>`, `blocked by <ids>`. JSON: `{"tickets": [...]}`, every frontmatter
  field (empty lists and nulls, not omitted, so agents need no defaults) plus `file`,
  `children`, `blocks`, `openBlockers`, `progress`. 3 tests on the `valid` fixture, JVM and
  linuxX64.
- **2026-10-02** · step 3 · `safanoria board [-o FILE]`; rendering in `core` (`Board.markdown`)
  for apps. Children are shown only under their parent (a child of a missing parent stands on
  its own), so section counts are of top-level tickets. Facets after the title only when not
  the usual (type other than feature, priority high/urgent, a child's status unless done), then
  `done/total` and `blocked by` (linked; a missing blocker in backticks). Closed sections list
  id and title only. Tickets with an unreadable status get their own last section. Links use
  `/` on every OS. Missing output directory: exit 2. Tests: golden file
  (`cli/src/commonTest/fixtures/board/valid.md`, CRLF-normalised for Windows checkouts),
  `--output` links, core edge cases; JVM and linuxX64.

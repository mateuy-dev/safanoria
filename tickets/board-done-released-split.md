---
id: board-done-released-split
type: feature
title: "Board: split done into to-release and released"
status: in-progress
priority: medium
size: S
created: 2026-10-07
updated: 2026-10-07
---

## Objective

The board has one `done` column, but done tickets are really two groups, and which group a ticket is in is very interesting information: those already in a released version (deployed) and those merged but waiting for the next release (to deploy). Today the board hides that: you can't see what the next release will bring, or whether a fix has reached users.

Show the two groups on the board instead of the single `done` column.

The data is already there, no new status is needed: a `done` ticket is stamped with `resolvedIn.<component>` by `safanoria-cli release` (SPEC §9), so *to release* = `done` with a component of its `area` not yet stamped, and *released* = every component of its `area` stamped. The split is a view over `status` + `resolvedIn`, not a change to the status set.

Decided (2026-10-07, with the user):

- **A view, not new statuses.** The status set stays as it is; `done` is shown as two columns, *to release* and *released*, in place of the single `done` column.
- **Names**: "to release" / "released". "Deployed" would assume a deployment, while Safanoria only knows releases (`resolvedIn`).
- **Released is collapsed by default**, as `done` is today (`BoardViewModel`): it only grows. *To release* is open: it is what the next version brings. `wontfix` is unchanged.
- **Several components**: a ticket with `area: [a, b]` stamped for `a` only stays in *to release* until every component of its `area` is stamped.
- **Nothing pending means released**: `done` tickets that never get `resolvedIn` (`research`, SPEC §9), or with no `area`, go in *released*.
- **Scope**: the GUI board (`gui/.../board`) and the CLI `board` command show the same split.

Not decided: showing the version on the tickets of the released column. Fine to add if it comes cheap, not required.

## Acceptance Criteria

- [ ] The GUI board shows *to release* and *released* where it showed `done`; no ticket status is added or changed.
- [ ] A `done` ticket with a component of its `area` not in `resolvedIn` is in *to release*; with all of them stamped, in *released*.
- [ ] A ticket with `area: [a, b]` stamped for `a` only is in *to release*.
- [ ] A `done` `research` ticket, and a `done` ticket with no `area`, are in *released*.
- [ ] *Released* is collapsed by default and *to release* is open; `wontfix` behaves as before.
- [ ] `safanoria-cli board` prints the same two groups instead of one `done` section, with the same rule.
- [ ] After `safanoria-cli release`, the tickets it stamped move from *to release* to *released*.

## Work Log

- **2026-10-07** · status · started

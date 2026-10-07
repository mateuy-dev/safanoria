---
id: board-done-released-split
type: feature
title: "Board: split done into to-release and released"
status: done
priority: medium
size: S
created: 2026-10-07
updated: 2026-10-07
resolvedIn:
  safanoria: 0.4.0
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
- **Nothing pending means released**: `done` tickets that never get `resolvedIn` (`research`, SPEC §9), or with no `area`, go in *released*. Except that in a project with one component a ticket with no `area` is of that component, as `release` reads it (changed 2026-10-07, see Work Log).
- **Scope**: the GUI board (`gui/.../board`) and the CLI `board` command show the same split.

Not decided: showing the version on the tickets of the released column. Fine to add if it comes cheap, not required.

## Acceptance Criteria

- [x] The GUI board shows *to release* and *released* where it showed `done`; no ticket status is added or changed.
- [x] A `done` ticket with a component of its `area` not in `resolvedIn` is in *to release*; with all of them stamped, in *released*.
- [x] A ticket with `area: [a, b]` stamped for `a` only is in *to release*.
- [x] A `done` `research` ticket, and a `done` ticket with no `area` in a project with several components (or none), are in *released*.
- [x] In a project with one component, a `done` ticket with no `area` is in *to release* until that component is stamped.
- [x] *Released* is collapsed by default and *to release* is open; `wontfix` behaves as before.
- [x] `safanoria-cli board` prints the same two groups instead of one `done` section, with the same rule.
- [x] After `safanoria-cli release`, the tickets it stamped move from *to release* to *released*.

## Work Log

- **2026-10-07** · status · started
- **2026-10-07** · decision · The rule is "what `release` would still stamp" (`Release.pending`, shared with stamping), not "an `area` entry without `resolvedIn`". So with one component a ticket with no `area` is to release: `release` stamps those, and this repository's tickets have no `area`, so the literal rule would have shown everything as released. An `area` entry that is not a component is never stamped, so it doesn't hold a ticket in to release.
- **2026-10-07** · decision · The two groups are `BoardGroup` in core, used by the CLI board and the GUI columns, so both have the same rule and names. The status set, `list` and its order are untouched.
- **2026-10-07** · decision · Added the versions (`app 4.3.0`) on released tickets, on the CLI board and the GUI cards: it came cheap. To release has its own colour in the GUI; released keeps done's.
- **2026-10-07** · status · review
- **2026-10-07** · status · done
- **2026-10-07** · release · safanoria 0.4.0

---
id: board-done-released-split
type: feature
title: "Board: split done into to-release and released"
status: backlog
priority: medium
size: S
created: 2026-10-07
updated: 2026-10-07
---

## Objective

The board has one `done` column, but done tickets are really two groups, and which group a ticket is in is very interesting information: those already in a released version (deployed) and those merged but waiting for the next release (to deploy). Today the board hides that: you can't see what the next release will bring, or whether a fix has reached users.

Show the two groups on the board instead of the single `done` column.

The data is already there, no new status is needed: a `done` ticket is stamped with `resolvedIn.<component>` by `safanoria-cli release` (SPEC §9), so *to release* = `done` with a component of its `area` not yet stamped, and *released* = every component of its `area` stamped. The split is a view over `status` + `resolvedIn`, not a change to the status set.

Open, to decide when the ticket is worked:

- Whether this really replaces `done` with two columns, or keeps one `done` column split into two groups inside it. Released only grows, so it should probably stay collapsed by default (the GUI collapses `done` and `wontfix` today, `BoardViewModel`), with *to release* open.
- Several components: a ticket with `area: [a, b]` stamped for `a` only. Simplest is *to release* until all are stamped; a board filtered by component could use that component alone.
- `done` tickets that never get `resolvedIn` (`research`, SPEC §9) belong to neither group as defined above: they need a place (probably with released, as nothing is pending for them).
- Column names: "deployed" assumes a deployment, while Safanoria only knows releases (`resolvedIn`); "to release" / "released" matches the spec's vocabulary.
- Scope: the GUI board (`gui/.../board`) and the CLI `board` command should agree; the released column could show the version.

## Acceptance Criteria

## Work Log


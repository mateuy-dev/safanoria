---
id: gui-versions-screen-listing
type: feature
title: "GUI: versions screen listing the tickets of each version"
status: backlog
priority: medium
size: S
created: 2026-10-07
updated: 2026-10-07
---

## Objective

A new screen in the GUI to see, for each version, which tickets it brings: what each release fixed or added. Today that is only visible one version at a time, through `safanoria-cli notes <component> <version>`, or ticket by ticket in `resolvedIn`; there is no overview of the project's history by version.

Keep it simple: just a list of versions, newest first, with under each version the tickets resolved in it.

The data is already there: `done` tickets are stamped with `resolvedIn.<component>: <version>` by `safanoria-cli release` (SPEC §9), so the screen is a view grouping tickets by `resolvedIn`, with the versions ordered as versions (0.10.0 after 0.9.0), not as text.

Open, to decide when working on it:

- Several components: one list per component, or a component selector. This project has a single component.
- Whether `done` tickets not yet stamped appear on top as the next, unreleased version (the board's *to release* column already shows them, see `board-done-released-split`).
- How a parent and its children are shown (SPEC §9: the feature as a whole shipped at the highest version among them).

## Acceptance Criteria

## Work Log


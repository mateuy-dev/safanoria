---
id: interactive-menus-make-descriptions
type: feature
title: "Interactive menus: make descriptions less prominent"
status: backlog
priority: medium
size: S
created: 2026-10-05
updated: 2026-10-05
---

## Objective

The interactive lists (the `safanoria` action menu and the other `Prompts.choose` / `chooseMany` lists in `cli/.../Prompts.kt`, built on Mordant's `interactiveSelectList`) are very difficult to read: each entry's description is rendered as prominently as its label, so the labels, which are what the person is scanning for and choosing between, don't stand out.

Make the descriptions visually secondary (e.g. dim/gray, via the list's description style) so the labels read first and the description is supporting detail. While there, check the rest of the list's readability (the cursor/selected entry is clearly distinguishable, the title stands apart from the entries) and that it stays legible on light and dark terminal themes and without colour (`NO_COLOR`, dumb terminals).

## Acceptance Criteria

## Work Log


---
id: interactive-cli
type: feature
title: Interactive cli
status: in-progress
priority: medium
size: S
created: 2026-10-02
updated: 2026-10-02
related: [cli-all-actions]
---

## Objective

Make the `safanoria` CLI interactive, so a person in a terminal can work with tickets without remembering flags. Two forms, to be decided when planning (one, the other, or both):

- **Prompts**: commands ask for missing values (title, type, parent, id…) and offer choices, instead of requiring every flag.
- **Full-screen TUI**: a terminal app to browse the board, open tickets and run actions from the keyboard.

Scripts and agents must keep working: the non-interactive behaviour stays the default when stdin is not a terminal.

## Acceptance Criteria

## Plan

## Work Log

- **2026-10-02** · status · started

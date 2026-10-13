---
id: gui-parent-children-board
type: feature
title: "GUI: kanban of the children on a parent ticket's screen"
status: in-progress
priority: medium
size: S
area: [safanoria]
created: 2026-10-13
updated: 2026-10-13
---

## Objective

When a parent ticket is open in the GUI, the status of its children is hard to see: its Plan lists them, but not where each one stands. Show, at the top of a parent ticket's screen, a kanban board of its children, one column per status, like the general board of all tickets but restricted to that parent's children. A ticket without children shows no board.

## Acceptance Criteria

- [x] A ticket with children shows, above its body, a board with the general board's columns and only its children as cards.
- [x] A card opens the child's screen.
- [x] A ticket without children shows no board.

## Work Log

- **2026-10-13** · status · started
- **2026-10-13** · decision · The children board is as tall as its cards and scrolls with the ticket's body, rather than being a fixed band above it: a parent has few children, and the body stays the main thing on the screen.
- **2026-10-13** · decision · Columns without children are collapsed and columns aren't toggled, unlike the general board (Released and Won't fix collapsed, click to toggle): the board shares the width with the fields panel, and on a parent the done children are the progress one looks for.
- **2026-10-13** · decision · Cards are in the parent's Plan order, not priority then id, and don't say their parent. The Children links in the side panel stay.

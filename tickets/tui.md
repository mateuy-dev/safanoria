---
id: tui
type: feature
title: "Console app (TUI) similar to the GUI, with keyboard and mouse"
status: in-progress
priority: medium
size: L
area: [safanoria]
created: 2026-10-21
updated: 2026-10-21
---

## Objective

Safanoria has a desktop GUI (gui module: board, ticket screen, versions screen, with actions like start, finish, merge) and a CLI of one-shot commands, but nothing in between for working from a terminal. Build a nice terminal (TUI) app similar to the GUI: the same main views (board with status columns and filters, ticket detail, versions) and the same actions on tickets, usable both with the keyboard (shortcuts, navigation) and with the mouse (clicking cards, buttons and tabs). It should reuse core for reading and changing tickets, as the GUI and CLI do, so its behaviour matches theirs. Useful where the GUI isn't (over SSH, inside Herdr/tmux, without a desktop).

## Acceptance Criteria

## Work Log

- **2026-10-21** · status · started
- **2026-10-21** · decision · Built on Mordant 3.0.2 (already in the CLI through Clikt), drawing the screens on our own cell buffer: it reads keys and mouse events in raw mode on JVM and every native target, so the TUI ships inside the native `safanoria-cli` (`safanoria-cli tui`, and plain `safanoria-cli` on a terminal in a repository). Rejected Mosaic (no mouse events), Lanterna (JVM only) and Kotter (no mouse).
- **2026-10-21** · decision · The GUI's UI-free state (board columns and cards, ticket and versions view state, `TicketStore` with start/finish/merge/reopen) moved from `gui` to a new multiplatform `views` module that both apps use, so they can't drift apart. `java.time` became kotlinx-datetime for native.

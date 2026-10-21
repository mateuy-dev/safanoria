---
id: tui
type: feature
title: "Console app (TUI) similar to the GUI, with keyboard and mouse"
status: backlog
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


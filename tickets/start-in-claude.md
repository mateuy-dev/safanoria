---
id: start-in-claude
type: feature
title: "GUI: Start in Claude button on backlog tickets"
status: in-progress
priority: medium
size: S
created: 2026-10-05
updated: 2026-10-06
---

## Objective

In the GUI, a backlog (or ready) ticket has a Start button: it starts the ticket (branch, worktree, `in-progress`) and opens a terminal there (`TicketAction.START`, `gui/.../data/Terminal.kt`). The user then still has to type `claude` and tell it what to do.

Add a second button next to it, "Start in Claude". It does the same as Start, and the terminal it opens runs Claude Code in the ticket's worktree with a first prompt telling it to start working on the ticket (e.g. `claude "Start working on ticket <id>"`), so one click goes from a backlog ticket to a session already working on it.

If possible, the terminal tab title is the ticket name, so several ticket sessions can be told apart. How to set it depends on the terminal emulator (a title flag of the emulator, an escape sequence, or naming the Claude session); Claude Code may overwrite the tab title itself, so check what holds.

Start stays as it is, for those who don't use Claude Code or want a plain terminal. If `claude` isn't installed, the button says so (or isn't offered) instead of opening a terminal that fails.

## Acceptance Criteria

## Work Log

- **2026-10-06** · status · started

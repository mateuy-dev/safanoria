---
id: herdr-integration
type: feature
title: Herdr integration
status: review
priority: medium
size: S
created: 2026-10-19
updated: 2026-10-19
related: [start-ticket-new-tmux]
---

## Objective

First integration of Safanoria with Herdr (a terminal multiplexer for coding agents). Goal: let a user work Safanoria tickets from Herdr, e.g. starting a ticket and getting a Herdr workspace/tab/pane opened in its worktree running Claude Code, the same idea as `start-ticket-new-tmux` but for Herdr. This is a first, small step: settle what the minimal useful integration is (a skill section, a CLI flag, a script), how Herdr is detected (`HERDR_ENV=1`), and what is left for later. The `herdr` skill documents how to control panes, tabs and workspaces.

## Acceptance Criteria

- [x] `safanoria-cli start <id> --herdr` inside Herdr opens the worktree in a workspace (size L, XL) or a tab (smaller) running Claude Code, without taking focus; `--in` overrides
- [x] `--implement` does the same with a session that implements and finishes the ticket
- [x] `safanoria-cli merge` inside Herdr closes the workspace or tab of the ticket's worktree, last; `--keep-herdr` skips it

## Work Log

- **2026-10-19** · status · started
- **2026-10-19** · decision · In the CLI (`start --herdr/--implement`, `merge`), not only the skill, so it works from any Herdr pane and the GUI. Layout follows the ticket's `size` (L/XL workspace, else tab). On merge, close what has a pane in the ticket's worktree, whichever pane runs it; a workspace closes whole only when all its tabs are in it, and the current one last because it ends the process. Opt-out is a `--keep-herdr` flag, not a `safanoria.yaml` setting, to stay small.
- **2026-10-19** · status · review

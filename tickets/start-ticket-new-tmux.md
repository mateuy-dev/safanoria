---
id: start-ticket-new-tmux
type: feature
title: Start a ticket in a new tmux window from a Claude session
status: backlog
priority: medium
size: S
created: 2026-10-17
updated: 2026-10-17
related: [start-in-claude, cli-start-should-cd]
---

## Objective

From a Claude Code session running inside tmux, the user wants to say "start <id> in tmux" and get the ticket started (branch, worktree, `in-progress`) with a new tmux window opened in its worktree, running Claude Code there. Each ticket then has its own window, next to the session that started it, without opening terminals or copying paths by hand.

The pieces exist: `safanoria-cli start <id> --print-path` prints only the worktree path (`cli-start-should-cd`), and the GUI's "Start in Claude" already runs `claude --name <id> "<first prompt>"` so the session is named after the ticket (`start-in-claude`). The core is:

    dir=$(safanoria-cli start <id> --print-path) && tmux new-window -d -n <id> -c "$dir" claude --name <id> "Start working on ticket <id>"

Things to settle:

- How it is offered: a skill section, a `/start-tmux <id>` slash command, a script, or a `--tmux` flag on `start`. The skill rule "never start a ticket" still holds: it runs only when the user asks.
- Outside tmux (`$TMUX` empty) there is no session to add a window to: create a detached session named after the ticket (`tmux new-session -d -s <id> -c "$dir" ...`) and tell the user to `tmux attach -t <id>`, or refuse with that hint.
- `-d` keeps focus on the current window; the window name is the ticket id. tmux names can't contain `.` or `:`, so sanitise ids that do.
- If `claude` isn't installed or `start` fails, open nothing and say why.

## Acceptance Criteria

## Work Log


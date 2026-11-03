---
id: herdr-prompt-start-timeout
type: bug
title: Herdr start with a first prompt reports a false timeout
status: backlog
priority: medium
size: S
created: 2026-11-03
updated: 2026-11-03
requests:
  - user: null
    who: Project maintainer
    channel: other
    date: 2026-11-03
---

## Objective

`safanoria-cli start <id> --herdr --prompt implement|chat` (also `--implement` and the `/sshi`, `/sshc`, `/scshi`, `/scshc` skills) opens the Herdr tab or workspace and starts the agent with its first prompt passed as an agent argument (`Herdr.open` in `core/.../Herdr.kt`). `herdr agent start` only succeeds once the agent is detected **and ready for input** (idle), waiting up to `--timeout` (default 30 s, max 300 s). With a first prompt the agent goes straight to working and is not idle within the timeout, so herdr returns `timeout waiting for agent startup`; `Herdr.open` treats that as "the agent didn't start" and `start` exits 1 with "Started, but not opened in Herdr … Claude didn't start there", although the agent is running and does the work. Skills, scripts and agents that check the exit code see a failure that didn't happen. Without a prompt (default mode) the agent starts idle and this doesn't happen.

Fix chosen: start the agent without the first prompt (so `agent start`'s readiness wait means what it says), then submit the prompt with `herdr agent prompt <name> "<prompt>"`. Rejected: treating a timeout as success when `agent get` shows the agent working (depends on reading herdr's error codes, and hides real startup failures); raising `--timeout` (an implement turn can outlast the 300 s maximum). A failure of either step still leaves the started ticket and the pane, and names the agent that failed, as today. Apply to both Claude and Codex.

### Steps to reproduce

1. Inside Herdr, `safanoria-cli start <id> --implement` on a backlog ticket.

### Expected

Exit 0 and "opened a tab '<id>' running Claude Code"; the session implements the ticket.

### Actual

Exit 1: `Started, but not opened in Herdr: opened a tab for <id> (pane …) but Claude didn't start there: herdr agent start failed (1): {"error":{"code":"timeout","message":"timed out waiting for agent startup"}…}`, while Claude runs in the tab and implements the ticket. Seen 2026-11-03 starting herdr-workspace-by-hierarchy.

## User Requests

> create a ticket, start it in herdr, implement it and merge it
— Project maintainer · other · 2026-11-03

## Acceptance Criteria

## Work Log

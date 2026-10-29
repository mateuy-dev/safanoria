---
id: choose-session-agent
type: feature
title: Choose Claude or Codex for ticket sessions
status: in-progress
priority: medium
size: XL
area: [safanoria]
created: 2026-10-29
updated: 2026-10-29
requests:
  - user: null
    who: Project maintainer
    channel: other
    date: 2026-10-29
  - user: null
    who: Project maintainer
    channel: other
    date: 2026-10-29
---

## Objective

Let users choose Claude Code or Codex when opening a Safanoria ticket session. Deliver shared CLI, skill, configuration and setup support first, then expose the same choice in both GUI and TUI. Preserve existing Claude behavior when no preference is supplied.

## User Requests

> For now we have skills that allways open claude. We want to be able to chose if we open claude or codex.
> Create a plan
— Project maintainer · other · 2026-10-29

> create a ticket for this. Create two child tickets. The firs one what you mention, the second one GUI and tui
— Project maintainer · other · 2026-10-29

## Acceptance Criteria

- [ ] Users can select Claude Code or Codex through CLI and launch skills, GUI, and TUI.
- [ ] Explicit selection overrides the project default; absent a preference, existing Claude behavior is preserved.
- [ ] Both agents receive sufficient project and ticket context to work normally.
- [ ] Both child tickets are complete.

## Plan

- [x] `choose-session-agent-cli`: Choose Claude or Codex in CLI and launch skills
- [ ] `choose-session-agent-ui`: Choose Claude or Codex in GUI and TUI

## Work Log

- **2026-10-29** · status · started

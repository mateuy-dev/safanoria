---
id: choose-session-agent-ui
type: feature
title: Choose Claude or Codex in GUI and TUI
status: backlog
priority: medium
size: L
area: [safanoria]
created: 2026-10-29
updated: 2026-10-29
parent: choose-session-agent
blockedBy: [choose-session-agent-cli]
requests:
  - user: null
    who: Project maintainer
    channel: other
    date: 2026-10-29
---

## Objective

Expose Claude Code and Codex as session choices in both the desktop GUI and terminal TUI. Reuse the shared agent model, project default and launch construction delivered by choose-session-agent-cli so users get consistent behavior from every interface. Preserve plain terminal access and report missing executables or launch failures clearly.

## User Requests

> create a ticket for this. Create two child tickets. The firs one what you mention, the second one GUI and tui
— Project maintainer · other · 2026-10-29

## Acceptance Criteria

- [ ] Both GUI and TUI let users choose Claude Code or Codex when starting an agent session.
- [ ] Both interfaces honor the shared project default and allow explicit selection to override it, retaining Claude as the fallback.
- [ ] Both interfaces use the shared agent launch construction rather than maintaining separate hardcoded CLI arguments.
- [ ] GUI sessions open in the ticket worktree through the existing platform terminal integration.
- [ ] TUI launches the selected agent through its existing Herdr and non-Herdr paths; returning from a local session restores the TUI normally.
- [ ] Plain terminal actions remain available.
- [ ] Missing selected executables are reported before starting the ticket where possible; failures after starting clearly describe the resulting ticket/worktree state without silently switching agents.
- [ ] Tests cover selection/defaults, launch arguments, missing executables, and failures in both interfaces; GUI and TUI launches are manually smoke-tested.

## Plan

1. Reuse the shared agent model, defaults, and command builders from `choose-session-agent-cli`.
2. Generalize the shared ticket action model and agent-specific labels so GUI and TUI expose both choices clearly.
3. Update GUI action handling, executable checks, and terminal launch behavior for the selected agent.
4. Update TUI action handling and workplace launching, covering both Herdr and local sessions while preserving shell access.
5. Update interface documentation and run targeted tests and manual launches for both agents.

## Design

The GUI currently hardcodes `START_IN_CLAUDE`, a `CLAUDE` executable constant, and Claude launch arguments in `gui/src/jvmMain/kotlin/dev/mateuy/safanoria/gui/ticket/TicketViewModel.kt`. The TUI dispatches the same action in `tui/src/commonMain/kotlin/dev/mateuy/safanoria/tui/TicketPage.kt` and uses a Claude boolean and hardcoded command in `tui/src/commonMain/kotlin/dev/mateuy/safanoria/tui/Workplace.kt`.

Inspect the shared action/view-state definitions and terminal abstractions before choosing the final UI controls. Keep the interaction focused on choosing the agent; leave CLI argument details out of the product UI.

## Work Log


---
id: choose-session-agent-ui
type: feature
title: Choose Claude or Codex in GUI and TUI
status: in-progress
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

- [x] Both GUI and TUI let users choose Claude Code or Codex when starting an agent session.
- [x] Both interfaces honor the shared project default and allow explicit selection to override it, retaining Claude as the fallback.
- [x] Both interfaces use the shared agent launch construction rather than maintaining separate hardcoded CLI arguments.
- [x] GUI sessions open in the ticket worktree through the existing platform terminal integration.
- [x] TUI launches the selected agent through its existing Herdr and non-Herdr paths; returning from a local session restores the TUI normally.
- [x] Plain terminal actions remain available.
- [x] Missing selected executables are reported before starting the ticket where possible; failures after starting clearly describe the resulting ticket/worktree state without silently switching agents.
- [ ] Tests cover selection/defaults, launch arguments, missing executables, and failures in both interfaces; GUI and TUI launches are manually smoke-tested.

Automated coverage for the final criterion passes in views, GUI and TUI JVM tests. An isolated
PTY smoke test also exercised both TUI agent choices through real process launching using
stand-in executables, verified their arguments and worktree, and navigated the restored TUI.
Manual GUI launches and sessions with the real agents remain unchecked: desktop control tools
are unavailable in this session.

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

- **2026-10-29** · status · started
- **2026-10-29** · decision · Show both explicit agent actions, ordering and marking the project default (Claude Code when absent); keep Start as a plain terminal action. Agent sessions wait for instructions, using the shared argument builder with no initial prompt.
- **2026-10-29** · decision · Preserve the created worktree in launch failure notices so users can recover without changing the selected agent; shell-quote the tmux agent command to preserve Codex configuration arguments.
- **2026-10-29** · validation · `./gradlew allTests --offline` passed (JVM and Linux native tests; macOS and Windows execution skipped on Linux). Both TUI choices passed isolated PTY process smoke checks with stand-in agents; manual GUI and real-agent sessions remain open for review.

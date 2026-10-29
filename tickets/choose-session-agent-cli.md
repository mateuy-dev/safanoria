---
id: choose-session-agent-cli
type: feature
title: Choose Claude or Codex in CLI and launch skills
status: backlog
priority: medium
size: L
area: [safanoria]
created: 2026-10-29
updated: 2026-10-29
parent: choose-session-agent
requests:
  - user: null
    who: Project maintainer
    channel: other
    date: 2026-10-29
---

## Objective

Add explicit and default agent selection to ticket session launching through the CLI and the six Herdr launch skills. Provide reusable agent-specific launch construction and ensure Codex receives the Safanoria context it needs. This child covers the previously agreed plan; GUI and TUI interaction is handled by the sibling ticket.

## User Requests

> create a ticket for this. Create two child tickets. The firs one what you mention, the second one GUI and tui
— Project maintainer · other · 2026-10-29

## Acceptance Criteria

- [ ] `safanoria-cli start` accepts `--agent claude|codex` and rejects unsupported values.
- [ ] Agent selection resolves as explicit flag, then project default in `safanoria.yaml`, then Claude.
- [ ] Both agents launch in Herdr with the existing workspace/tab behavior and waiting, discussion, and implementation modes.
- [ ] `ssh`, `sshc`, `sshi`, `scsh`, `scshc`, and `scshi` accept optional agent selection and pass it to the CLI without including it in ticket text.
- [ ] `init`, `update`, and context discovery support Codex access to the Safanoria skill, spec, commands, and project/ticket instructions while preserving existing instructions.
- [ ] Agent-specific help, launch instructions, and errors identify the selected agent accurately.
- [ ] Config schema, specification, and README describe the default and explicit overrides.
- [ ] Meaningful automated coverage verifies precedence, invalid selection, both agents and all three prompt modes, argument quoting, and launch failures; both agent paths are manually smoke-tested in Herdr.

## Plan

1. Introduce a shared agent choice and project default, updating the config parser, schema, and specification.
2. Replace the Claude boolean in `Herdr.open` with agent selection while preserving shell-only support. Verify Herdr Codex support and actual CLI arguments before implementing the agent-specific command builders; do not pass Claude-only flags to Codex.
3. Update `StartCommand` to resolve the selected agent, construct the appropriate launch command and initial prompt, and print matching help and recovery instructions.
4. Update all six launch skills, keeping their existing names and prompt modes. Handle the optional agent argument separately from ticket ids and creation text.
5. Extend installation/update and context discovery for both agents, using shared source content and preserving existing project instructions. Verify Codex discovery and context mechanisms rather than assuming Claude hooks apply.
6. Update documentation and run the targeted checks and manual launches.

## Design

Current launch behavior is hardcoded in `cli/src/commonMain/kotlin/dev/mateuy/safanoria/cli/StartCommand.kt` and `core/src/commonMain/kotlin/dev/mateuy/safanoria/core/Herdr.kt`. The six skill sources live in `skill/commands/`. Setup and context handling currently refer to Claude directories and its SessionStart hook.

Examples of the proposed interface:

```sh
safanoria-cli start ticket-id --herdr --agent codex
safanoria-cli start ticket-id --prompt implement --agent claude
```

Keep agent-specific command construction reusable by the GUI/TUI child. No new skill variants per agent are needed. GUI and TUI interaction changes belong to `choose-session-agent-ui`.

## Work Log


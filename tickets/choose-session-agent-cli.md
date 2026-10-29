---
id: choose-session-agent-cli
type: feature
title: Choose Claude or Codex in CLI and launch skills
status: in-progress
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

- [x] `safanoria-cli start` accepts `--agent claude|codex` and rejects unsupported values.
- [x] Agent selection resolves as explicit flag, then project default in `safanoria.yaml`, then Claude.
- [x] Both agents launch in Herdr with the existing workspace/tab behavior and waiting, discussion, and implementation modes.
- [x] `ssh`, `sshc`, `sshi`, `scsh`, `scshc`, and `scshi` accept optional agent selection and pass it to the CLI without including it in ticket text.
- [x] `init`, `update`, and context discovery support Codex access to the Safanoria skill, spec, commands, and project/ticket instructions while preserving existing instructions.
- [x] Agent-specific help, launch instructions, and errors identify the selected agent accurately.
- [x] Config schema, specification, and README describe the default and explicit overrides.
- [x] Meaningful automated coverage verifies precedence, invalid selection, both agents and all three prompt modes, argument quoting, and launch failures; both agent paths are manually smoke-tested in Herdr.

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

## Learnings

- Codex discovers repository skills under `.agents/skills` and user skills under `~/.agents/skills`, independently of `CODEX_HOME`; `AGENTS.md` supplies project instructions. It can use `CLAUDE.md` as a configured fallback, while the Codex Safanoria instructions explicitly read shared Claude guidance and invoke `context --agent codex` when work begins.
  → promoted: README.md, skill/SKILL.md
- Herdr 0.9.5 supports `agent start --kind codex`; Codex accepts a positional initial prompt and `-c` configuration overrides, but not Claude's `--name` flag. First-use trust dialogs return `agent_not_ready` while leaving the agent and pane alive.
  → promoted: README.md

## Work Log

- **2026-10-29** · status · started
- **2026-10-29** · decision · Keep native agent argument construction in core `SessionAgent` and make the Herdr agent nullable for shell-only launches. The existing TUI caller only adapts to the API; agent-selection interaction remains in the sibling ticket.
- **2026-10-29** · decision · Install the same skill/spec/command sources in both agents' discovery directories. Codex uses additive AGENTS.md instructions and explicit context discovery instead of relying on Claude's SessionStart hook; existing project guidance and unmarked user commands retain the existing confirmation behavior.
- **2026-10-29** · validation · Native CLI launches of both agents in disposable ticket worktrees exercised first-use trust failures and retained their panes. Matching Herdr launch arguments in this already trusted worktree then reached idle/interactive-ready for both Claude and Codex without an initial prompt; new workspace/tab placement kept focus unchanged. Closed only the smoke-test workspaces and left trust dialogs unanswered.
- **2026-10-29** · validation · `./gradlew allTests` passed: 298 JVM tests and 268 Linux native tests, with no failures. Coverage includes selection precedence and invalid values, both agents and all prompt modes, workspace/tab construction, literal shell quoting, startup failures, skill metadata, Codex installation/discovery, preserved instructions, and detached review context.

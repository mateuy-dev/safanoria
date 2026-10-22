---
id: slash-commands-most-often
type: feature
title: Slash commands for the most often run workflows
status: in-progress
priority: medium
size: S
created: 2026-10-22
updated: 2026-10-22
---

## Objective

Many workflows are typed to Claude over and over in nearly the same words, e.g. "create a new ticket that does X, and open a Herdr session for it". Define Claude Code slash commands (skills with disable-model-invocation) for the most frequent ones, so each is one short command with the ticket text as argument. Example: /scish (safanoria create issue, start herdr) = create the ticket with the safanoria skill, commit it on main, then run safanoria-cli start <id> --herdr. First find which workflows are actually run most often (past sessions, the skill's flows: create, create+start, review, finish review, merge), then pick short memorable names. Decide whether they ship with the safanoria skill (so every project using Safanoria gets them) or stay personal/project-local.

## Acceptance Criteria

- [x] `/sc`, `/scsh`, `/scshc`, `/scshi`, `/ssh`, `/sshc`, `/sshi`, `/sr`, `/sre`, `/sm` exist as user-only skills
- [x] `start --prompt none|chat|implement`; `--herdr` alone starts Claude with no prompt
- [x] `init`/`update` install the commands with the skill

## Work Log

- **2026-10-22** · status · started
- **2026-10-22** · decision · Commands: /sc (create), /scsh, /scshc, /scshi (create, then start in Herdr with no prompt, chat or implement), /ssh, /sshc, /sshi (the same for an existing ticket), /sr, /sre, /sm (review, end review, merge). Picked from past prompts: ticket creation, mostly followed by starting it, and merging are the most typed; commit, push and release are left out as not Safanoria-specific. A /scs without Herdr was dropped: a command can't move the session into the new worktree.
- **2026-10-22** · decision · `start --prompt none|chat|implement` chooses the Herdr session's first prompt; `--implement` stays as its alias. The old "Start working on ticket <id>" is dropped everywhere (CLI, TUI, GUI Start in Claude) for no prompt: it was ambiguous, and the SessionStart hook already gives the session its ticket.
- **2026-10-22** · decision · The commands ship with Safanoria: sources in `skill/commands/<name>.md`, embedded in the CLI and installed by `init`/`update` as `.claude/skills/<name>/SKILL.md` (skills with `disable-model-invocation`), marked like the skill. A same-named skill without the marker is the project's and is replaced only after asking. The create rules they share (no questions, a self-contained Objective from the conversation, on mainBranch) live in the safanoria skill's Create, so each command stays a few lines.

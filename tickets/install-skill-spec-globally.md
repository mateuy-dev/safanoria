---
id: install-skill-spec-globally
type: feature
title: Install the skill and SPEC globally
status: review
priority: medium
size: M
created: 2026-10-22
updated: 2026-10-22
related: [slash-commands-most-often]
---

## Objective

The skill and SPEC.md are installed per project (.claude/skills/safanoria/, committed). After updating Safanoria, every project and every existing worktree keeps the old copy until main is merged into its branch, so the agent may follow a skill that doesn't match the safanoria-cli on PATH (which embeds the current skill and spec). Ticket diffs also carry skill/spec updates.

Proposal: safanoria-cli install --global (or update --global) writes the skill and SPEC to ~/.claude/skills/safanoria/, so one update reaches all projects and worktrees and the skill always matches the CLI. Make it the default; init asks (or takes a flag) whether the project also commits its own copy, which is still needed for cloud/phone sessions (Claude Code on the web only has the repo, not ~/.claude), other contributors and CI. A project should not have both copies (two skills with the same name; check which one Claude Code picks). validate or the SessionStart hook warns when a project copy is older than the CLI. Consider spec versions: a single global SPEC must not mislead projects on an older spec version once the spec has a breaking version 2. Slash commands from slash-commands-most-often, if they ship with Safanoria, would install the same way.

## Acceptance Criteria

- [x] `safanoria-cli update --global` installs this version's skill and SPEC in `~/.claude/skills/safanoria/` (`$CLAUDE_CONFIG_DIR` when set), from anywhere.
- [x] `init` installs them there; the project's own copy only when asked (`--project-skill`, or the question on a terminal).
- [x] `update` in a project updates the user's copy, and the project's only when it has one.
- [x] The install scripts and `make install-cli` run `update --global` (not with `SAFANORIA_CLI_ONLY`).
- [x] On a ticket's branch, the SessionStart context names the SPEC in use and warns when the skill in use isn't from the CLI's version, or is missing.
- [x] A global SPEC on another spec version than the project's doesn't mislead: the skill sends the agent to the project's copy.

Out of scope:
- Slash commands (`slash-commands-most-often`): they would install the same way, in `~/.claude/commands/`.
- A `validate` warning about stale project copies: CI pins CLI versions, so it would warn about newer copies too.

## Learnings

- Claude Code: a personal skill (`~/.claude/skills/<name>`) wins over a project skill of the same name, so both copies can coexist; cloud sessions don't read `~/.claude` (they need the committed copy or a claude.ai-enabled skill).
  → promoted: README.md
- Consequence for this repository: once the user's copy is installed, sessions here use it, not the working copy of `skill/SKILL.md` symlinked from `.claude/skills/safanoria/`.
  → ticket only

## Work Log

- **2026-10-22** · status · started
- **2026-10-22** · decision · Both copies may exist: Claude Code prefers the user's skill to the project's (docs, "Resolve skills that share a name"), so locally the one matching the CLI wins and the committed copy only serves cloud sessions and contributors. No "not both" check needed.
- **2026-10-22** · decision · `update --global` rather than a new `install` command: `update` already means "this version's skill"; without `--global`, `update` in a project does both, and keeps the project's copy only if one exists (never adds one).
- **2026-10-22** · decision · The context warning checks the skill Claude Code will use (user's if present, else project's), not every copy: a stale project copy behind a current user copy changes nothing locally.
- **2026-10-22** · decision · Spec versions: the CLI rejects a `safanoria.yaml` on a spec version it doesn't implement, so a global SPEC can't silently meet one through the CLI; for agents, the skill compares SPEC.md's title with `safanoria:` and falls back to the project's copy. Revisit when spec 2 exists (install one SPEC per version).
- **2026-10-22** · decision · Tests set `CLAUDE_CONFIG_DIR` (build/test-claude) so `init`/`update` tests never write the developer's `~/.claude`; tests that check it pass their own directory to `cli()`.
- **2026-10-22** · status · review

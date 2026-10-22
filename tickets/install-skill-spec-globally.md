---
id: install-skill-spec-globally
type: feature
title: Install the skill and SPEC globally
status: in-progress
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

## Work Log

- **2026-10-22** · status · started

---
id: v1-tooling
type: feature
title: Tooling and open points to make Safanoria v1 usable across projects
status: backlog
priority: high
size: L
created: 2026-10-01
updated: 2026-10-01
---

## Objective

`SPEC.md` v1, the agent skill and the ticket template exist, and VacAppKMP is being set up as the
first project using them (branch `safanoria` in VacAppKMP). Everything that keeps the format honest
and easy to adopt is still missing: nothing validates tickets, the skill is installed by copying
files by hand, there is no board, and `resolvedIn` stamping has no tool to call.

This ticket collects the remaining work for Safanoria itself. It can be split into child tickets
when work starts. Project-specific work (VacApp release stamping in `AdminMain`, moving
`docs/bugs` and `docs/plans` into tickets, Rails versioning) belongs in the projects' own tickets.

## Acceptance Criteria

- [ ] This repository is under git and uses Safanoria for its own tickets
- [ ] A validator reports every rule in SPEC §12, with file and line
- [ ] A ticket can be created, listed and released (stamped) from the command line
- [ ] A board view shows tickets by status, with parents, children and blocked tickets
- [ ] The skill and spec can be installed in, and updated for, a project with one command
- [ ] A project can run validation automatically before commits and in CI
- [ ] The open points below are decided and written into SPEC.md

Out of scope:
- Interactive viewer/editor apps (later tickets, after the CLI exists)
- MCP server

## Plan

- [ ] `git init` this repository; first commit with SPEC, skill, template, this ticket.
- [ ] Decide the CLI language and distribution. Options: Kotlin (JVM, matches existing projects,
      slow start), Kotlin/Native binary, or a script language. Startup time matters for hooks.
- [ ] `schema/`: JSON Schema for the frontmatter and for `safanoria.yaml`, used by the CLI and apps.
- [ ] CLI `validate`: all SPEC §12 checks; exit code ≠ 0 on errors; machine-readable output option.
- [ ] CLI `new`: suggests an id from the title, checks file and branch uniqueness, writes the template.
- [ ] CLI `board`: generates a markdown board (by status; parents with children and progress;
      blocked tickets marked).
- [ ] CLI `release <component> <version>`: stamping as in SPEC §9; reads the version from the
      component's `version` source when not given.
- [ ] Pre-commit hook and a CI example (GitHub Actions) running `validate`.
- [ ] Install/update command for the skill and spec into a project's `.claude/skills/safanoria/`,
      replacing the manual copy (copies drift from this repo).
- [ ] Skill: on Start, switch the agent session into the new worktree (see Learnings).
- [ ] Decide and specify the open points:
      - Stamping components released from another repository (`external: true`, e.g. VacApp's Rails
        server): who runs `release`, and how it reaches this ticket repository.
      - Versioning of Safanoria itself (spec version vs tool version), to replace
        `external: true` in this repository's `safanoria.yaml`.
      - Attachments: naming, size limits, how apps show them.
      - Whether templates per type are needed (e.g. `research` with questions as criteria).

## Learnings

- Creating a worktree with `git worktree add` does not move a Claude Code session into it; the
  session keeps working in the original checkout until it switches (EnterWorktree with `path`).
- `.claude/settings.local.json` is ignored by git, so it is not present in new worktrees: local
  settings such as extra directories must be added again in each worktree.

## Work Log

- **2026-10-01** · status · Created from the design discussion while setting up VacAppKMP.

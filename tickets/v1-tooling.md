---
id: v1-tooling
type: feature
title: Tooling and open points to make Safanoria v1 usable across projects
status: in-progress
priority: high
size: L
created: 2026-10-01
updated: 2026-10-01
related: [gui-viewer]
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

- [x] This repository is under git and uses Safanoria for its own tickets
- [ ] A validator reports every rule in SPEC §12, with file and line
- [ ] A ticket can be created, listed and released (stamped) from the command line
- [ ] A board view shows tickets by status, with parents, children and blocked tickets
- [ ] The skill and spec can be installed in, and updated for, a project with one command
- [ ] A project can run validation automatically before commits and in CI
- [ ] The open points (Safanoria versioning, external stamping, attachments, per-type templates)
      are decided and written into SPEC.md
- [ ] This repository's own tickets pass `validate`, and its `safanoria.yaml` no longer needs
      `external: true` for the `safanoria` component

Out of scope:
- Interactive viewer/editor apps (later tickets, after the CLI exists; first: `gui-viewer`)
- MCP server

## Plan

Children merge into this branch (`childrenMergeInto: parent`, the default), so the v1 tooling
ships as one set. One child per command or deliverable that can be reviewed on its own; small
skill and README edits stay as own steps. `cli-core` is split out of `validate` because every
command needs the same parser and writer; once it is done, `validate`, `new`, `board` and
`release` can be built in any order.

- [x] `git init` this repository; first commit with SPEC, skill, template, this ticket.
      Done in 4c2f71b, before this ticket started.
- [x] Skill: on Start, switch the agent session into the new worktree (see Learnings), and say
      that `.claude/settings.local.json` must be recreated there. First, because every child
      below starts a worktree.
- [x] Decide the CLI language and distribution, and record it here.
      Decision: **Kotlin Multiplatform**. A `core` module (commonMain: model, parser, writer,
      validator, operations) shared by a `cli` module built with **Kotlin/Native** (one binary per
      OS: linuxX64, macosArm64, mingwX64; ~10 ms startup for hooks, no runtime to install) and,
      later, a Compose Desktop viewer on the JVM (`gui-viewer`). A JVM target also runs the tests.
      Writing tickets uses targeted text edits (status, `updated`, `resolvedIn`, Work Log, Plan
      checkboxes), so the YAML library is only used for reading and no round-trip library is
      needed. JS target deferred: only needed for npm distribution or a web viewer.
      Rejected: TypeScript on Node (no code sharing with a Compose GUI; adds Node to Kotlin
      projects), Kotlin/JVM only (~0.5 s startup in hooks; stays available as fallback, since
      `core` runs on the JVM), Kotlin/JS (weaker for file system and processes), Go (no reuse).
- [x] `v1-tooling-native-spike`: prove the Kotlin/Native libraries and git calls before
      `cli-core` builds on them.
- [ ] `v1-tooling-schema`: JSON Schema for the frontmatter and `safanoria.yaml`.
- [ ] `v1-tooling-cli-core`: KMP `core` + `cli` modules, config loading, ticket parser with line
      numbers, targeted-edit writer.
- [ ] `v1-tooling-validate`: all SPEC §12 checks.
- [ ] `v1-tooling-new`: `new` command.
- [ ] `v1-tooling-board`: `list` and `board` commands.
- [ ] `v1-tooling-spec-decisions`: decide and specify Safanoria's versioning, attachments and
      per-type templates.
- [ ] `v1-tooling-release`: `release` stamping, including components released from another
      repository (`external: true`).
- [ ] `v1-tooling-install`: install/update of skill, spec and template into a project.
- [ ] `v1-tooling-hooks`: pre-commit hook and GitHub Actions example running `validate`.
- [ ] README: replace the manual setup steps and the "Planned" list with the CLI; give the
      `safanoria` component a `version` source per the versioning decision; run `validate` on
      this repository.

## Learnings

- Creating a worktree with `git worktree add` does not move a Claude Code session into it; the
  session keeps working in the original checkout until it switches (EnterWorktree with `path`).
  → promoted: skill/SKILL.md
- `.claude/settings.local.json` is ignored by git, so it is not present in new worktrees: local
  settings such as extra directories must be added again in each worktree.
  → promoted: skill/SKILL.md

## Work Log

- **2026-10-01** · status · Created from the design discussion while setting up VacAppKMP.
- **2026-10-01** · status · Started. Branch `v1-tooling` from `main`, worktree `../safanoria--v1-tooling`.
  Includes the uncommitted `worktree:` line from `main`'s `safanoria.yaml`.
- **2026-10-01** · plan · Split into eight children. The language decision stays an own step
  before `cli-core`, since all children depend on it. `list` was in Acceptance Criteria but not in
  the Plan; added to `board`. External-component stamping moved into `release` (it shapes that
  command); the other open points into `spec-decisions`. Added a criterion for dogfooding.
- **2026-10-01** · plan · Correction to the previous entry: the split had nine children, not eight.
- **2026-10-01** · plan · Language decided with the user: Kotlin Multiplatform, CLI on
  Kotlin/Native, so a future Compose Desktop viewer reuses `core` (TypeScript recommendation
  dropped). The targeted-edit writer removes the need for a YAML round-trip library. Added the
  research child `v1-tooling-native-spike`, since library support on Native is the main risk;
  `cli-core` is blocked by it. `install` now also blocked by `cli-core`. Viewer recorded as
  `gui-viewer` (out of scope, `related`).
- **2026-10-01** · plan · Moved `hooks` after `install`, which now blocks it. Removed the reverse
  `related` from `gui-viewer` (SPEC §8: one side only).
- **2026-10-01** · step 2 · Skill Start step now says to switch the session into the worktree,
  to carry over needed uncommitted changes (happened at this ticket's start), and to recreate
  `settings.local.json`. Both Learnings promoted there.
- **2026-10-01** · publish · Published at github.com/mateuy-dev/safanoria (public, MIT). History
  rewritten before the first push to remove private project names from the SPEC.md examples and
  to add LICENSE; the first commit is now 4c2f71b (was 8b23b85).
- **2026-10-01** · v1-tooling-native-spike · Done and merged: Kotlin/Native works on all three
  targets. JSON Schema validator kept in the CLI (Linux binary needs `libunistring.so.5`).
  Findings in the Design sections of `cli-core`, `validate` and `install`.

---
id: v1-tooling
type: feature
title: Tooling and open points to make Safanoria v1 usable across projects
status: review
priority: high
size: L
created: 2026-10-01
updated: 2026-10-02
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
- [x] A validator reports every rule in SPEC §12, with file and line
- [x] A ticket can be created, listed and released (stamped) from the command line
- [x] A board view shows tickets by status, with parents, children and blocked tickets
- [x] The skill and spec can be installed in, and updated for, a project with one command
- [x] A project can run validation automatically before commits and in CI
- [x] The open points (Safanoria versioning, external stamping, attachments, per-type templates)
      are decided and written into SPEC.md
- [x] This repository's own tickets pass `validate`, and its `safanoria.yaml` no longer needs
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
- [x] `v1-tooling-schema`: JSON Schema for the frontmatter and `safanoria.yaml`.
- [x] `v1-tooling-cli-core`: KMP `core` + `cli` modules, config loading, ticket parser with line
      numbers, targeted-edit writer.
- [x] `v1-tooling-validate`: all SPEC §12 checks.
- [x] `v1-tooling-new`: `new` command.
- [x] `v1-tooling-board`: `list` and `board` commands.
- [x] `v1-tooling-spec-decisions`: decide and specify Safanoria's versioning, attachments and
      per-type templates.
- [x] `v1-tooling-release`: `release` stamping, including components released from another
      repository (`external: true`).
- [x] `v1-tooling-install`: install/update of skill, spec and template into a project.
- [x] `v1-tooling-hooks`: pre-commit hook and GitHub Actions example running `validate`.
- [x] README: replace the manual setup steps and the "Planned" list with the CLI; run
      `validate` on this repository. (The `safanoria` component's `version` source was done in
      `v1-tooling-spec-decisions`; the setup steps and the CLI items of "Planned" in
      `v1-tooling-install`. Left: a final read of the README against what shipped.)

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
- **2026-10-01** · v1-tooling-schema · Done and merged: both schemas, 56 examples,
  `schema/check.py` in CI; SPEC §4 now says dates and versions are strings.
- **2026-10-02** · v1-tooling-cli-core · Done and merged: Gradle build with `core` and `cli`,
  config, frontmatter and body parsing with lines, schema checks, targeted editor, git,
  `Repository`; CI tests on Linux, Windows and macOS. 16–26 ms to load this repository.
- **2026-10-02** · v1-tooling-validate · Done and merged: `safanoria validate` (all §12 rules,
  files / `--staged` / JSON), 35 fixtures, CI validates our tickets; `schema/check.py` removed.
- **2026-10-02** · v1-tooling-new · Done and merged: `safanoria new` (id suggestion, template,
  parent Plan item, `--dry-run`); usage errors exit 2 in every command; skill Create uses it.
- **2026-10-02** · v1-tooling-board · Done and merged: `safanoria list` and `safanoria
  board`, on `TicketGraph` in `core`.
- **2026-10-02** · v1-tooling-spec-decisions · Done and merged: Safanoria version vs spec
  version (SPEC §13; `gradle.properties` is this repository's version source, so `external:
  true` is gone and that criterion is checked), attachments (§7.8, two `validate` rules),
  per-type templates (§6.2, built-in `bug` and `research`). Also a `Makefile` with `make
  install`. Open-points criterion stays open until `release` decides external stamping.
- **2026-10-02** · v1-tooling-release · Done and merged: `safanoria release <component>
  [<version>]` (version sources, `--ticket`, `--dry-run`, refuses off `mainBranch`); SPEC §9
  external-component flow. All open points are now decided and written into SPEC.md.
- **2026-10-02** · v1-tooling-install · Done and merged: `safanoria init` and `update` (skill
  and spec embedded in the binary, version marker, project files only changed after asking),
  release workflow and install scripts (tested on the three OSes; publishing waits for the
  first tag), README setup replaced. VacAppKMP's hand copy replaced by `update` (its commit
  `04408caa`).
- **2026-10-02** · v1-tooling-hooks · Done and merged: `safanoria hook install|uninstall` (lets
  commits through without the CLI, never touches another tool's hook) and the composite Action
  `uses: mateuy-dev/safanoria@vX.Y.Z`, tested on the three OSes. Hook installed in this clone.
- **2026-10-02** · step 13 · README read against what shipped: a command overview near the top
  (matches `safanoria --help`); install says releases start at `v0.1.0` (`make install` until
  then); the board example is now a fixed illustration (this repository's own went stale with
  every merge, and with every child done showed no statuses or blockers); the embedded-files
  notes merged; `action.yml` added to what runs the release workflow's test mode. `validate`:
  this repository's 12 tickets are valid.
- **2026-10-02** · status · review. Every child done, every criterion met. Next, outside this
  ticket: merge `v1-tooling` into `main`, stamp (`safanoria release safanoria`), tag `v0.1.0`.

---
id: v1-tooling-install
type: feature
title: Install the CLI, and set up or update Safanoria in a project with one command
status: backlog
priority: high
size: M
created: 2026-10-01
updated: 2026-10-01
parent: v1-tooling
blockedBy: [v1-tooling-cli-core, v1-tooling-spec-decisions]
---

## Objective

Two parts:

- **Getting the CLI**: the `safanoria` binary is built by CI for each OS (linuxX64, macosArm64,
  mingwX64) and published as release assets, with a small install script that downloads the
  right one.
- **Setting up a project**: today the skill and spec are copied by hand into a project's
  `.claude/skills/safanoria/`, and the copies drift from this repository. One command should set
  up a project (README "Adding Safanoria to a project") and later update it to the current
  version.

## Acceptance Criteria

- [ ] CI builds and publishes the three binaries on each Safanoria release; an install script
      puts the right one on the PATH
- [ ] `safanoria init`: creates `safanoria.yaml` (asking for components), the ticket directory
      with `_TEMPLATE.md` and `README.md`, and installs the skill
- [ ] `safanoria update`: replaces the installed skill, spec and template with the current
      version, and says which version was installed before and after
- [ ] Does not overwrite a project's `_TEMPLATE.md` or `CLAUDE.md` without asking
- [ ] VacAppKMP's manual copy replaced using it

## Plan

## Work Log

- **2026-10-01** · plan · Blocked also by `cli-core` (`init`/`update` are CLI commands). Added
  binary distribution, since the CLI is a Kotlin/Native binary per OS; size S → M.

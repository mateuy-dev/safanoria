---
id: v1-tooling-install
type: feature
title: Install the CLI, and set up or update Safanoria in a project with one command
status: backlog
priority: high
size: M
created: 2026-10-01
updated: 2026-10-02
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
      with `_TEMPLATE.md`, the per-type templates (`_TEMPLATE.bug.md`, `_TEMPLATE.research.md`)
      and `README.md`, and installs the skill. Installed copies end with
      `<!-- safanoria X.Y.Z -->` (SPEC §13)
- [ ] `safanoria update`: replaces the installed skill, spec and template with the current
      version, and says which version was installed before and after
- [ ] Does not overwrite a project's `_TEMPLATE.md` or `CLAUDE.md` without asking
- [ ] VacAppKMP's manual copy replaced using it

## Plan

## Design

From `v1-tooling-native-spike` (workflow at commit 657ea65, `.github/workflows/native-spike.yml`):

- Build each binary on its own runner: ubuntu-24.04 (linuxX64), windows-2022 (mingwX64),
  macos-14 (macosArm64). Cold builds take 2–4.5 min; cache `~/.konan` and Gradle caches.
- The Linux job must create the `libunistring.so` symlink before linking (see
  `v1-tooling-validate`), and the Linux binary only runs where `libunistring.so.5` exists
  (Ubuntu 24.04+). Say so in the install docs.
- Binary sizes: 5.4–5.8 MB. Windows binary is `safanoria.exe`, others `safanoria.kexe` (rename to
  `safanoria` when publishing).

## Work Log

- **2026-10-01** · plan · Blocked also by `cli-core` (`init`/`update` are CLI commands). Added
  binary distribution, since the CLI is a Kotlin/Native binary per OS; size S → M.
- **2026-10-02** · plan · From `v1-tooling-spec-decisions`: `init` also installs the per-type
  templates (a project's `_TEMPLATE.md` wins over the built-in ones, so without them a project
  never gets the bug template), and installed copies carry the version marker. A `Makefile`
  (`make install`) builds and installs from a checkout meanwhile.

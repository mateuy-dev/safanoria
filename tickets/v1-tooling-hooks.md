---
id: v1-tooling-hooks
type: feature
title: Pre-commit hook and CI example running validate
status: in-progress
priority: medium
size: S
created: 2026-10-01
updated: 2026-10-02
parent: v1-tooling
blockedBy: [v1-tooling-validate, v1-tooling-install]
---

## Objective

A project can run `safanoria validate` automatically before each commit and in CI, so invalid
tickets do not reach the main branch. Both call the native `safanoria` binary; CI gets it with
the install script from `v1-tooling-install`.

## Acceptance Criteria

- [ ] A pre-commit hook that validates staged tickets, installable with one command or snippet
- [ ] A GitHub Actions example workflow that installs the binary and runs `validate`, documented
      in README.md
- [ ] This repository uses both (CI: see Plan; its own `tickets.yml` keeps building the CLI
      from the commit it validates)

## Plan

- [x] `safanoria hook install|uninstall [--dry-run]`: writes `pre-commit` where git looks for
      hooks (`git rev-parse --git-path hooks`: respects `core.hooksPath` and is shared by all
      worktrees). The hook runs `safanoria validate --staged`; when `safanoria` isn't on PATH it
      warns and lets the commit through (a teammate without the CLI isn't blocked; CI still
      checks). A `pre-commit` that isn't Safanoria's is never overwritten: it prints the line to
      add instead. `uninstall` removes only Safanoria's. `init` ends by suggesting it. Logic
      (script, location, existing hook's kind) in `core`. Tests on a temporary git repository.
- [ ] A GitHub Action, `action.yml` at the root (composite): installs the binary with
      `install.sh` / `install.ps1` (input `version`, default latest), puts it on PATH, runs
      `safanoria validate` (input `args`). Projects add `uses: mateuy-dev/safanoria@v0.1.0`
      after `actions/checkout`. Tested by the release workflow's test mode with `uses: ./`
      against the built binaries, on the three OSes. This repository's `tickets.yml` keeps
      building the CLI from the commit: its tickets must pass the validator of that same commit,
      which a released binary is not.
- [ ] README: hook (command, and the one-line snippet for projects managing hooks otherwise)
      and the Action example. This repository: hook installed in this clone; Development says
      to run `safanoria hook install`.

## Work Log

- **2026-10-01** · plan · Blocked also by `install`: CI needs the published binary.
- **2026-10-02** · status · Started. Branch `v1-tooling-hooks` from `v1-tooling`, worktree
  `../safanoria--v1-tooling-hooks`.
- **2026-10-02** · plan · A `hook` command rather than a snippet only; the hook lets commits
  through when the CLI is missing. CI as a reusable composite Action rather than a workflow to
  copy, tested in the release workflow. This repository's CI stays on the from-source build.
- **2026-10-02** · step 1 · `safanoria hook install|uninstall [--dry-run]`. `Git.hooksDir()`:
  `git rev-parse --path-format=absolute --git-path hooks` (git 2.31+), so `core.hooksPath` and
  worktrees (hooks live in the common dir) are handled. `Hooks` in `core`: script, state (none,
  Safanoria's by its marker line, another tool's), install; `makeExecutable` is a new
  expect/actual (POSIX `chmod` 0755 on native, `File.setExecutable` on the JVM). Another tool's
  hook is never touched: `install` prints the line to add and exits 1. `init` now mentions
  `hook install`. Tests on a hand-made minimal `.git` (HEAD, objects, refs) under `build/`, so
  no `git init` and this repository's hooks are never touched; executable bit checked with
  `test -x` off Windows. Core 1 test, CLI 2, JVM and linuxX64; compiles for mingwX64 and
  macosArm64.

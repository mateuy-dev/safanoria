---
id: v1-tooling-native-spike
type: research
title: Can the Safanoria CLI be built with Kotlin/Native?
status: in-progress
priority: high
size: S
created: 2026-10-01
updated: 2026-10-01
parent: v1-tooling
---

## Objective

The CLI is planned as a Kotlin/Native binary sharing a KMP `core` with a future Compose Desktop
viewer. Before `cli-core` builds on it, prove the pieces work on linuxX64, macosArm64 and
mingwX64. Time box: size S. If it fails, fall back to a JVM CLI on the same `core` and record why.

## Acceptance Criteria

- [ ] Which YAML library reads frontmatter on all native targets (candidates: kaml,
      snakeyaml-engine-kmp)?
- [ ] Do Clikt (CLI) and Okio (file system) work on all three targets?
- [ ] Is a KMP JSON Schema validator usable (candidate: OptimumCode `json-schema-validator`), or
      are per-file rules written in Kotlin?
- [ ] How does the CLI run `git` and read its output on each target (branch existence, staged
      files)?
- [ ] Measured startup time of a hello-world native binary that loads `safanoria.yaml`
- [ ] Can CI cross-build all three binaries, and on which runners (macOS needs a macOS runner)?

## Plan

Throwaway code in `spike/`, a KMP Gradle project (targets: jvm, linuxX64, mingwX64,
macosArm64). Everything is built and run here on linuxX64. mingwX64 is cross-compiled from Linux
but can't be run here (no Windows, no Wine). macosArm64 can't be built here: Apple targets need
a macOS host. Those two targets are built and run on GitHub Actions runners (cross-build step).

Constraint: the disk has ~2.6 GB free. Use what's cached (Gradle 9.x, Kotlin/Native 2.3/2.4
toolchains, kaml 0.77.1, Okio 3.11). The mingw toolchain download (~0.5 GB) is the only big
addition. If it doesn't fit, skip it and answer from metadata.

- [ ] Gradle skeleton in `spike/` (wrapper from cache, Kotlin 2.4.x, the four targets). A
      linuxX64 `main` that prints "hello".
- [ ] YAML: parse `safanoria.yaml` and the frontmatter of every ticket in `tickets/` with kaml
      on linuxX64 and jvm. Check that errors and nodes carry line numbers (needed by `validate`).
      Try `snakeyaml-engine-kmp` only if kaml falls short.
- [ ] Clikt and Okio: a `safanoria hello <path>` command, with Clikt subcommands and Okio file
      reads, on linuxX64. For mingwX64 and macosArm64, check the published artifacts.
- [ ] JSON Schema: validate one frontmatter against a small schema with OptimumCode
      `json-schema-validator` on linuxX64 (frontmatter YAML → JsonElement). Record the binary
      size and startup cost it adds. If it's unusable, record that per-file rules go in Kotlin
      (and `v1-tooling-validate` drops `blockedBy: v1-tooling-schema`).
- [ ] git: run `git branch --list <id>` and `git diff --cached --name-only` from native code.
      Try `popen` (posix, linux/macOS) and see what mingw needs (`_popen`), behind
      `expect`/`actual`. Compare with a process library if one exists for all three targets.
- [ ] Startup: release binary loading `safanoria.yaml` and all tickets; time 20 runs (`hyperfine`
      if available, else a shell loop). Same for the JVM jar, for comparison.
- [ ] Cross-build: a GitHub Actions workflow on this branch with a matrix (ubuntu for linuxX64,
      windows for mingwX64, macos-14 for macosArm64) that builds the spike binary and runs it
      against this repository's tickets with the same timing. This answers the macOS and Windows
      questions with real runs. Also try linking `mingwX64` on Linux, if the disk allows.
- [ ] Answer every question in Acceptance Criteria; write Learnings with where each one goes
      (`cli-core`, `validate`, `install`, `v1-tooling` Plan). Delete `spike/` in the last commit,
      so only the ticket merges into `v1-tooling`; the Learnings name the commit that still has
      the code, for `cli-core` to reuse.

## Learnings

- From inside a worktree session, Claude Code's `EnterWorktree` with `path` refuses a sibling
  worktree (only paths under `.claude/worktrees/` are allowed then). The session must first go
  back with `ExitWorktree` (`keep`) and then enter the new worktree. This affects starting a child
  ticket while working on its parent.

## Work Log

- **2026-10-01** · status · Started. Branch `v1-tooling-native-spike` from `v1-tooling`
  (`childrenMergeInto: parent`), worktree `../safanoria--v1-tooling-native-spike`.
- **2026-10-01** · plan · Spike code in `spike/`, deleted before review (research: the
  deliverable is Learnings). Only linuxX64 and jvm can run here; mingwX64 compile only;
  macosArm64 from docs and metadata, with CI as the real proof in `install`.
- **2026-10-01** · plan · The repository is now public on GitHub (`mateuy-dev/safanoria`), so the
  cross-build step runs real CI on Linux, Windows and macOS runners instead of only drafting it.

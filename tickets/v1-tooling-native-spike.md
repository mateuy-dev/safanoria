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

- [x] Gradle skeleton in `spike/` (wrapper from cache, Kotlin 2.4.x, the four targets). A
      linuxX64 `main` that prints "hello".
- [x] YAML: parse `safanoria.yaml` and the frontmatter of every ticket in `tickets/` with kaml
      on linuxX64 and jvm. Check that errors and nodes carry line numbers (needed by `validate`).
      Try `snakeyaml-engine-kmp` only if kaml falls short.
- [x] Clikt and Okio: a `safanoria hello <path>` command, with Clikt subcommands and Okio file
      reads, on linuxX64. For mingwX64 and macosArm64, check the published artifacts.
- [x] JSON Schema: validate one frontmatter against a small schema with OptimumCode
      `json-schema-validator` on linuxX64 (frontmatter YAML → JsonElement). Record the binary
      size and startup cost it adds. If it's unusable, record that per-file rules go in Kotlin
      (and `v1-tooling-validate` drops `blockedBy: v1-tooling-schema`).
- [x] git: run `git branch --list <id>` and `git diff --cached --name-only` from native code.
      Try `popen` (posix, linux/macOS) and see what mingw needs (`_popen`), behind
      `expect`/`actual`. Compare with a process library if one exists for all three targets.
- [x] Startup: release binary loading `safanoria.yaml` and all tickets; time 20 runs (`hyperfine`
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
- **2026-10-01** · step 1 · Kotlin 2.4.20, Gradle 9.3.0. Release linuxX64 "hello" binary: 479 KB.
  Declaring `mingwX64` makes the first build download the mingw toolchain (and LLVM 21), even when
  only linuxX64 is linked. Disk is no longer a constraint (21 GB free).
- **2026-10-01** · step 2 · kaml 0.104.0 (published for linuxX64, mingwX64, macosArm64) reads
  `safanoria.yaml` and all 12 tickets' frontmatter on linuxX64 and jvm with identical output.
  Every node has a 1-based line and column; syntax errors carry line and column too. Decoding to
  a `@Serializable` class works with `strictMode = false` (unknown fields ignored), but
  `MissingFieldException` has no line: `validate` must check the node tree, not rely on decoding.
  Dates stay strings (no YAML 1.1 timestamp conversion). snakeyaml-engine-kmp not needed.
  Okio used here already (file reads), a step early.
- **2026-10-01** · step 3 · Clikt 5.1.0 and Okio 3.18.2: subcommands, typed options, `--help`,
  and exit codes (`PrintMessage` with `statusCode`) work on linuxX64. Okio publishes all three
  native targets; with Clikt and kaml added, the code compiles (klib) for mingwX64 and
  macosArm64 on Linux too, so dependencies resolve on every target. Linking and running there is
  step 7. Release linuxX64 binary with kaml + Clikt + Okio: 4.2 MB.
- **2026-10-01** · step 4 · OptimumCode `json-schema-validator` 0.5.5 validates correctly (all
  tickets valid; a broken sample gives pattern, enum, type and required errors with JSON pointers,
  mapped back to file lines through kaml node locations). But on linuxX64 it does not link out of
  the box: its dependency `com.doist.x:normalize` links `-lunistring`, which Kotlin/Native's
  sysroot lacks. Linking against the system library (symlink in `build/native-libs`) works, but
  the binary then needs `libunistring.so.5` at runtime (missing on older distros, which ship
  `.so.2`), and grows from 4.2 to 5.8 MB. Proposal: no JSON Schema validator in the CLI; write
  per-file rules in Kotlin and keep `schema/` as a published artifact for editors (YAML language
  server), with a JVM test checking that rules and schema agree. Then `v1-tooling-validate` no
  longer needs `blockedBy: v1-tooling-schema`. To confirm with the user at the end.
- **2026-10-01** · step 5 · git through the shell with `popen` (linux, macOS) / `_popen` (mingw:
  `popen` does not exist there, so open/close are `expect`/`actual` per platform), `fgets` loop,
  `2>&1` to merge stderr. `pclose` returns a wait status on POSIX (exit code = bits 8-15),
  `_pclose` the exit code. JVM uses `ProcessBuilder` (`sh -c` / `cmd /c`). Branch existence,
  staged files and a failing command give the same output native and on the JVM. No process
  library tried: none needed. Limitation: arguments go through a shell, so quoting must suit
  both `sh` and `cmd.exe`; fine for ids and paths (`validate` only passes those). If arbitrary
  arguments are ever needed, use `posix_spawn` + pipes / `CreateProcess`. Windows run is step 7.
- **2026-10-01** · step 6 · `spike/bench.py` (no hyperfine here), linuxX64 release vs JVM 21 fat
  jar, wall time per process, file cache warm. `load` = read config + parse every frontmatter.

  | case | native mean (min) | JVM mean (min) |
  |---|---|---|
  | `--help` | 9 ms (6) | |
  | `load`, 12 tickets | 12 ms (7) | 246 ms (236) |
  | `load`, 504 tickets | 72 ms (61) | 353 ms (339) |
  | `schema` (validator init + 12 validations) | 12 ms (6) | |

  Native meets the < 100 ms hook target with room; it scales at ~0.12 ms per ticket, so ~800
  tickets would reach 100 ms for a full load (`validate` on staged files still needs every
  frontmatter for references). The JVM's ~240 ms fixed cost rules it out for hooks, as expected.

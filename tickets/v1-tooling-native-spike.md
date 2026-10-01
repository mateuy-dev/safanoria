---
id: v1-tooling-native-spike
type: research
title: Can the Safanoria CLI be built with Kotlin/Native?
status: review
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

Answer: **yes**, Kotlin/Native works for the CLI on all three targets.

- [x] Which YAML library reads frontmatter on all native targets (candidates: kaml,
      snakeyaml-engine-kmp)?
      kaml 0.104.0, with 1-based line and column on every node and on syntax errors.
      snakeyaml-engine-kmp not needed.
- [x] Do Clikt (CLI) and Okio (file system) work on all three targets?
      Yes: Clikt 5.1.0 and Okio 3.18.2, run on Linux, Windows and macOS in CI.
- [x] Is a KMP JSON Schema validator usable (candidate: OptimumCode `json-schema-validator`), or
      are per-file rules written in Kotlin?
      Usable: 0.5.5 works on all three, errors map to file lines. On Linux it needs
      `libunistring` at link time (workaround) and `libunistring.so.5` at runtime. Decided with
      the user: use it in the CLI; switch to Kotlin rules if older Linux distros must be supported.
- [x] How does the CLI run `git` and read its output on each target (branch existence, staged
      files)?
      Through the shell: `popen` (Linux, macOS) / `_popen` (Windows), `2>&1`, exit code from
      `pclose` (POSIX wait status) / `_pclose`. JVM: `ProcessBuilder`.
- [x] Measured startup time of a hello-world native binary that loads `safanoria.yaml`
      5–16 ms for `--help`, 9–20 ms to load this repository (12 tickets) depending on the OS;
      JVM ~245 ms. Tables in the Work Log (steps 6 and 7).
- [x] Can CI cross-build all three binaries, and on which runners (macOS needs a macOS runner)?
      Each on its own runner: ubuntu-24.04, windows-2022, macos-14 (cold build 2–4.5 min).
      mingwX64 also links on Linux; macOS needs macOS.

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
- [x] Cross-build: a GitHub Actions workflow on this branch with a matrix (ubuntu for linuxX64,
      windows for mingwX64, macos-14 for macosArm64) that builds the spike binary and runs it
      against this repository's tickets with the same timing. This answers the macOS and Windows
      questions with real runs. Also try linking `mingwX64` on Linux, if the disk allows.
- [x] Answer every question in Acceptance Criteria; write Learnings with where each one goes
      (`cli-core`, `validate`, `install`, `v1-tooling` Plan). Delete `spike/` in the last commit,
      so only the ticket merges into `v1-tooling`; the Learnings name the commit that still has
      the code, for `cli-core` to reuse.

## Learnings

- From inside a worktree session, Claude Code's `EnterWorktree` with `path` refuses a sibling
  worktree (only paths under `.claude/worktrees/` are allowed then). The session must first go
  back with `ExitWorktree` (`keep`) and then enter the new worktree. This affects starting a child
  ticket while working on its parent.
  → promoted: skill/SKILL.md
- The working spike (all commands, `bench.py`, CI workflow) is at commit 657ea65, deleted after
  it. `cli-core` can start from it.
  → promoted: `v1-tooling-cli-core`
- Stack: Kotlin 2.4.20, Gradle 9.3, kaml 0.104.0, Clikt 5.1.0, Okio 3.18.2. Platform code is
  small: file system (`FileSystem.SYSTEM`) and process (`popen`/`_popen`) only.
  → promoted: `v1-tooling-cli-core`
- kaml nodes and syntax errors carry 1-based lines; decoding to a `@Serializable` class with
  `strictMode = false` works but `MissingFieldException` has no line. Check the node tree.
  → promoted: `v1-tooling-cli-core`, `v1-tooling-validate`
- `json-schema-validator` 0.5.5 → `com.doist.x:normalize` links `-lunistring` on linuxX64:
  needs a `libunistring.so` symlink on the linker path, and the binary needs
  `libunistring.so.5` at runtime (Ubuntu 24.04+; older distros ship `.so.2`). Windows and macOS
  need nothing. Kept by decision.
  → promoted: `v1-tooling-validate`, `v1-tooling-install`
- Running git through `popen` passes arguments through `sh` / `cmd.exe`: only safe for ids and
  paths without quotes. Use `posix_spawn` / `CreateProcess` if arbitrary arguments are needed.
  → promoted: `v1-tooling-cli-core`
- Parsing costs ~0.12 ms per ticket on Linux and ~0.2 ms on the Windows/macOS runners; 504
  tickets take 63–136 ms. A large repository would miss the hook target if every command parses
  every full ticket.
  → promoted: `v1-tooling-cli-core`, `v1-tooling-validate`
- Each binary is built on its own OS runner (macOS needs macOS); cold builds 2–4.5 min, so
  cache `~/.konan`. Declaring `mingwX64` downloads the mingw toolchain on every first build,
  even when only linuxX64 is linked.
  → promoted: `v1-tooling-install`
- In a Claude Code worktree session, the sandbox refuses Bash commands whose text contains "git"
  in forms it can't verify (heredocs, loops, URLs with "github", a CLI subcommand named `git`).
  Use the Write/Edit tools and plain, separate git commands.
  → ticket only

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
- **2026-10-01** · step 7 · `mingwX64` links on Linux (5.6 MB `.exe`; not run, no Wine).
  `.github/workflows/native-spike.yml` builds each binary on its own OS (ubuntu-24.04,
  windows-2022, macos-14), runs every spike command against this repository, benchmarks it, and
  uploads it. Run 36897932693: all three green, identical command output on each OS (schema errors
  with lines, branch/staged/failing git, exit codes); Windows `_popen` through `cmd.exe` works,
  and Windows/macOS link the schema validator without extra libraries (only Linux needs
  `libunistring`). Build times with a cold cache: Linux 135 s, macOS 203 s, Windows 259 s.

  | runner | binary | `--help` | `load` 12 | `load` 504 |
  |---|---|---|---|---|
  | ubuntu-24.04 | 5.8 MB | 5 ms | 9 ms | 63 ms |
  | windows-2022 | 5.6 MB | 9 ms | 15 ms | 120 ms |
  | macos-14 | 5.4 MB | 16 ms | 20 ms | 136 ms |

  Windows and macOS runners are about 2x slower per ticket than Linux; 504 tickets exceed the
  100 ms target there. Fine for real projects today (tens of tickets), but `validate` on staged
  files should avoid parsing every full ticket when it only needs ids and relations, or cache.
- **2026-10-01** · step 8 · Decided with the user: keep `json-schema-validator` in the CLI and
  accept the Linux `libunistring.so.5` dependency ("If we need to run this in older ubuntus we'll
  change it latter"), so `v1-tooling-validate` stays blocked by `v1-tooling-schema`. Questions
  answered; Learnings promoted into `cli-core`, `validate` and `install` (Design sections) and
  into the skill. `spike/` and the workflow deleted; the code stays at 657ea65. No project tests
  exist yet to run (no CLI); the deliverable is this ticket.
- **2026-10-01** · status · review.

---
id: v1-tooling-cli-core
type: feature
title: KMP core module, CLI skeleton, ticket parser and targeted-edit writer
status: backlog
priority: high
size: M
created: 2026-10-01
updated: 2026-10-01
parent: v1-tooling
blockedBy: [v1-tooling-native-spike]
---

## Objective

The base every command builds on, as decided in `v1-tooling`'s Plan (Kotlin Multiplatform):

- Gradle project with a `core` module (commonMain; targets: JVM, linuxX64, macosArm64, mingwX64)
  and a `cli` module (Kotlin/Native executable `safanoria`). `core` holds all logic, so the
  future Compose Desktop viewer (`gui-viewer`) reuses it unchanged.
- CLI entry point (`safanoria <command>`), finding the repository root and `safanoria.yaml`.
- Loading the config with defaults (SPEC §2).
- Parsing every ticket: frontmatter, sections, checklists, Plan child items, Learnings with
  their `→` lines, Work Log entries, quotes; each with its line number for error reports.
- Writing tickets with **targeted text edits** (set a frontmatter field, add a `resolvedIn`
  entry, tick a checklist item, append a Work Log entry), never by re-serializing the YAML, so
  field order, unknown fields, comments and unknown sections stay untouched (SPEC §5, §7).
  `new` and `release` depend on this.

## Acceptance Criteria

- [ ] Parsing and then applying no edits to any ticket in this repository (and VacAppKMP's) gives
      the same bytes; each edit changes only the lines it targets
- [ ] Parse errors carry file and line
- [ ] The native CLI starts fast enough for a pre-commit hook (target: under 100 ms on this
      repository)
- [ ] `core` tests run on the JVM and on the native target

## Plan

## Design

From `v1-tooling-native-spike` (proven on Linux, Windows and macOS):

- Stack: Kotlin 2.4.20, Gradle 9.3, kaml 0.104.0 (YAML), Clikt 5.1.0 (CLI), Okio 3.18.2 (files),
  OptimumCode `json-schema-validator` 0.5.5 (see `v1-tooling-validate`). Start from the spike
  code at commit 657ea65 (`spike/`: build file, `expect`/`actual` layout, commands, `bench.py`).
- Read frontmatter as a kaml node tree: every node has a 1-based line and column (add the
  frontmatter's offset in the file). Decoding to a `@Serializable` class (`strictMode = false`)
  is fine for convenience, but its `MissingFieldException` has no line.
- Processes: `popen` (linuxMain, appleMain) / `_popen` (mingwMain; `popen` does not exist
  there), `2>&1`, exit code = `(pclose(p) shr 8) and 0xff` on POSIX, `_pclose(p)` on Windows,
  `ProcessBuilder` on the JVM. Arguments go through `sh`/`cmd.exe`: only pass ids and paths;
  switch to `posix_spawn`/`CreateProcess` if arbitrary arguments are ever needed.
- Startup: 5–16 ms for an empty command, ~0.12 ms (Linux) to ~0.2 ms (Windows/macOS) per parsed
  ticket. Parse lazily (frontmatter first, body only when a command needs it) so large
  repositories stay under the 100 ms target.

## Work Log

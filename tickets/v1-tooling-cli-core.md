---
id: v1-tooling-cli-core
type: feature
title: KMP core module, CLI skeleton, ticket parser and targeted-edit writer
status: in-progress
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

Layout: Gradle build at the repository root with modules `core/` and `cli/` (later `gui/` for
`gui-viewer`); versions in `gradle/libs.versions.toml`. Packages `dev.mateuy.safanoria.core`
and `dev.mateuy.safanoria.cli` (chosen by the user).
`core` reads lazily: listing tickets reads only frontmatter; the body is parsed when asked.

- [ ] Build skeleton: root Gradle project, `core` (jvm, linuxX64, mingwX64, macosArm64) and
      `cli` (native executables + JVM for tests); `safanoria version` runs. Two build tasks that
      keep sources single: generate `Schemas.kt` (string constants) from `schema/*.json`, so
      the CLI embeds the same schemas the repo publishes; and, on Linux, create the
      `libunistring.so` symlink the linker needs (spike learning) instead of a manual step.
      CI workflow `cli.yml`: build and run tests on ubuntu-24.04, windows-2022, macos-14
      (JVM tests + that OS's native tests), caching `~/.konan`.
- [ ] Config: find the repository root (walk up to `safanoria.yaml`), load it with SPEC §2
      defaults into `Config`, keeping the kaml node for lines. `Diagnostic(file, line, column,
      code, message)` is the one error type every command reports.
- [ ] Frontmatter: split it from the body (with its line offset), parse to a kaml node tree,
      typed accessors (`id`, `status`, `parent`, `blockedBy`…) that keep each value's line.
      `FrontmatterSchema` validates with the embedded ticket schema and maps JSON pointers to
      lines; same for `safanoria.yaml`. Tests run every `schema/examples/` file with its
      `# expect:` line, on JVM and native.
- [ ] Body: sections (name, heading line, range), checklists with continuation lines and child
      items (`` `id` `` first), Learnings with their `→` resolution (promoted / new ticket /
      ticket only / pending), Work Log entries (`date`, `ref`, text), User Requests quotes with
      attribution lines. Malformed parts become diagnostics, not exceptions.
- [ ] Targeted editor: `setField` (replace the line, or insert at its §5 position), `setMapEntry`
      (e.g. `resolvedIn.app`, turning `resolvedIn: null` into a block), `setChecked(item)`,
      `appendWorkLog(entry)`. It edits the original text, so unedited files are the same bytes;
      it keeps the file's line endings and final newline. Refuses values it can't edit safely
      (multi-line block scalars) with a diagnostic instead of guessing.
      Tests: no-op round trip, and each edit changes only its lines, over this repository's
      tickets and, when `SAFANORIA_EXTRA_REPOS` points at local checkouts (VacAppKMP), theirs.
      Private tickets are never copied into this repository.
- [ ] Processes and git in `core` (from the spike: `popen`/`_popen`/`ProcessBuilder`), and a
      `Repository` facade: config, ticket list (lazy), git helpers (`branchExists`,
      `stagedFiles`). Hidden `safanoria dump <file>` prints what the parser sees (debugging and
      a smoke test for the binary).
- [ ] Startup check: `tools/bench.py` (from the spike) on the release binary; record times for
      this repository and 504 tickets on each CI OS. Target: < 100 ms here.
- [ ] README: build, test and run instructions for contributors. `schema/check.py` stays until
      `validate` checks this repository's tickets in CI (note added to `v1-tooling-validate`).

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
- From `v1-tooling-schema`: `core` tests run every `schema/examples/` file through the production
  validator, honouring each invalid file's `# expect: <keyword> <pointer>` line. Once they pass,
  `schema/check.py` can be removed if nothing else needs it.

## Work Log

- **2026-10-01** · status · Started. Branch `v1-tooling-cli-core` from `v1-tooling`, worktree
  `../safanoria--v1-tooling-cli-core`.
- **2026-10-01** · plan · Root Gradle build with `core` and `cli`. Schemas embedded by code
  generation from `schema/*.json` (one source). The schema validator lives in `core` here (its
  examples test is this ticket's); cross-file rules stay in `validate`. VacAppKMP round-trip
  tests read local checkouts through `SAFANORIA_EXTRA_REPOS`; private tickets never committed.
- **2026-10-01** · plan · Approved, with packages under `dev.mateuy.safanoria` (user's choice).

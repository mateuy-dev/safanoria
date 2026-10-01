---
id: v1-tooling-cli-core
type: feature
title: KMP core module, CLI skeleton, ticket parser and targeted-edit writer
status: in-progress
priority: high
size: M
created: 2026-10-01
updated: 2026-10-02
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

- [x] Build skeleton: root Gradle project, `core` (jvm, linuxX64, mingwX64, macosArm64) and
      `cli` (native executables + JVM for tests); `safanoria version` runs. Two build tasks that
      keep sources single: generate `Schemas.kt` (string constants) from `schema/*.json`, so
      the CLI embeds the same schemas the repo publishes; and, on Linux, create the
      `libunistring.so` symlink the linker needs (spike learning) instead of a manual step.
      CI workflow `cli.yml`: build and run tests on ubuntu-24.04, windows-2022, macos-14
      (JVM tests + that OS's native tests), caching `~/.konan`.
- [x] Config: find the repository root (walk up to `safanoria.yaml`), load it with SPEC §2
      defaults into `Config`, keeping the kaml node for lines. `Diagnostic(file, line, column,
      code, message)` is the one error type every command reports.
- [x] Frontmatter: split it from the body (with its line offset), parse to a kaml node tree,
      typed accessors (`id`, `status`, `parent`, `blockedBy`…) that keep each value's line.
      `FrontmatterSchema` validates with the embedded ticket schema and maps JSON pointers to
      lines; same for `safanoria.yaml`. Tests run every `schema/examples/` file with its
      `# expect:` line, on JVM and native.
- [x] Body: sections (name, heading line, range), checklists with continuation lines and child
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
- **2026-10-02** · step 1 · Root Gradle build (wrapper 9.3.0, Kotlin 2.4.20, versions catalog),
  `core` (explicit API) and `cli`. `generateEmbedded` writes `Embedded.kt` (version + both
  schemas as multi-dollar raw strings, so `$schema` stays literal) into `core`'s generated
  sources. `linkUnistring` (root build, all KMP subprojects) symlinks the system
  `libunistring.so.5` for every linuxX64 link, including test binaries; it fails with a clear
  message when the library is missing. `safanoria version` runs; native Windows `.exe` 2.7 MB.
  `allTests` passes locally (JVM + linuxX64). `cli.yml` runs `allTests`, links and runs the
  binary on ubuntu-24.04, windows-2022 and macos-14.
- **2026-10-02** · step 2 · `ConfigLoader.findRoot/load/parse` → `Config` with §2 defaults,
  typed `Component`/`VersionSource`/`RefSystem`, and the line of every top-level key. Loading is
  lenient (wrong types fall back to defaults); only YAML syntax errors are reported here, since
  type and value rules come from the schema in step 3 (one source of rules). `Diagnostic`
  prints as `file:line:col: error[code]: message`. Internal `YamlBlock` maps kaml's block lines
  to file lines. 7 tests, JVM and linuxX64.
- **2026-10-02** · step 3 · `Frontmatter.parse` (bounds, CRLF-tolerant) → typed fields as
  `Located<T>` with file lines, enums with their spec spelling, `requests`, `resolvedIn`, all
  keys. `SchemaValidator` (internal) checks frontmatter and `safanoria.yaml` (now part of
  `ConfigLoader.parse`) against the embedded schemas and maps JSON pointers to file lines (a
  nested map or list points at its key's line). YAML→JSON typing follows YAML 1.2 but treats
  quoted and block scalars as strings, found from the source since kaml drops the style
  (`title: "2026"` is valid, `title: 2026` is a type error). Examples test: all 56 pass on JVM
  and linuxX64. Deviation: OptimumCode reports `anyOf`/`oneOf` as their failing branches and
  `propertyNames` at the offending key, where Python's jsonschema reports the combinator or the
  map; the test accepts the expected keyword, or any branch error, at or under the pointer.
  VacAppKMP's config and 49 tickets pass (`SAFANORIA_EXTRA_REPOS`). Fixed on the way: test
  environment variables are now Gradle task inputs; before, changing them reused a cached result.
- **2026-10-02** · step 1 · Fix: CI failed on Windows and macOS because `allTests` also linked a
  linuxX64 binary there (Kotlin/Native cross-links) and `linkUnistring` found no library. Linux
  link tasks are now disabled on non-Linux hosts; Linux binaries are built on Linux.
- **2026-10-02** · step 4 · `Ticket` (lazy: frontmatter and body parse separately) and `Body`:
  sections (headings inside fenced code blocks ignored), checklists with continuation lines and
  child items, Learnings with `Resolution`, Work Log entries (refs may contain spaces:
  `step 2`), User Requests quotes. Malformed entries, resolutions and attributions are
  diagnostics with their line. Section order and required content (§7.1) are left to
  `validate`. 27 tests on JVM and linuxX64; this repository's tickets parse cleanly, and the
  parent's Plan children equal the tickets naming it as parent. VacAppKMP: 47 tickets (49 files
  minus README and template; step 3's "49" was the file count), 0 parse diagnostics.

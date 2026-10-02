---
id: v1-tooling-install
type: feature
title: Install the CLI, and set up or update Safanoria in a project with one command
status: done
priority: high
size: M
created: 2026-10-01
updated: 2026-10-02
parent: v1-tooling
blockedBy: [v1-tooling-cli-core, v1-tooling-spec-decisions]
resolvedIn:
  safanoria: 0.1.0
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

- [x] CI builds and publishes the three binaries on each Safanoria release; an install script
      puts the right one on the PATH
- [x] `safanoria init`: creates `safanoria.yaml` (asking for components), the ticket directory
      with `_TEMPLATE.md`, the per-type templates (`_TEMPLATE.bug.md`, `_TEMPLATE.research.md`)
      and `README.md`, and installs the skill. Installed copies end with
      `<!-- safanoria X.Y.Z -->` (SPEC §13)
- [x] `safanoria update`: replaces the installed skill, spec and template with the current
      version, and says which version was installed before and after
- [x] Does not overwrite a project's `_TEMPLATE.md` or `CLAUDE.md` without asking
- [x] VacAppKMP's manual copy replaced using it

## Plan

The binary carries everything it installs (skill, SPEC.md, templates, embedded like the
schemas), so the installed files are exactly the binary's Safanoria version. Logic in `core`
(`Install` returns the files to write), the CLI asks and writes. Files fall in two kinds:

- **Managed** (`.claude/skills/safanoria/SKILL.md` and `SPEC.md`): Safanoria's, end with
  `<!-- safanoria X.Y.Z -->`, always replaced by `update`.
- **Project-owned once created** (`<dir>/_TEMPLATE*.md`, `<dir>/README.md`, `CLAUDE.md`,
  `safanoria.yaml`): created when missing; never changed without asking. Templates carry no
  marker (it would be copied into every ticket).

- [x] Embed `skill/SKILL.md` and `SPEC.md`. `Install` in `core`: the managed files with the
      marker, the version found in an installed copy's marker (none: installed by hand), the
      project files to create, and for templates whether they differ from the built-in ones.
- [x] `safanoria init [--dir] [--component NAME=FILE:PROPERTY]... [--external NAME]...
      [--dry-run]`: refuses when `safanoria.yaml` exists (use `update`). Without components it
      asks for them (names, then each one's version file and property, or external); without a
      terminal it needs the options. Writes `safanoria.yaml`, the ticket directory with the
      three templates and `README.md`, the skill, and the CLAUDE.md paragraph (creates CLAUDE.md,
      or asks before appending to an existing one). Validates what it wrote. Tests.
- [x] `safanoria update [--yes] [--dry-run]`: replaces the skill and spec, prints `skill and
      spec: <before> → <after>` (`installed by hand` when there is no marker); creates missing
      templates; for a template that differs from the built-in one, asks (`--yes` replaces,
      without a terminal it is kept and reported). Tests.
- [x] Release workflow `.github/workflows/release.yml`, on a `vX.Y.Z` tag: fails unless the
      tag equals `gradle.properties` `version`; builds the three binaries with `-Prelease`;
      publishes a GitHub release with `safanoria-linux-x64`, `safanoria-macos-arm64`,
      `safanoria-windows-x64.exe` and `SHA256SUMS`. Manual runs (`workflow_dispatch`) build and
      upload artifacts without publishing, to test it before the first tag.
- [x] `install.sh` (Linux, macOS) and `install.ps1` (Windows): download the latest release (or
      `SAFANORIA_VERSION`) for this OS, check the checksum, put it in `~/.local/bin` (or
      `SAFANORIA_BIN_DIR`) and say if that isn't on PATH; on Linux, warn when
      `libunistring.so.5` is missing. A base-URL override lets the test install from local files.
      README: install, `init`/`update` replace the manual "Adding Safanoria to a project" steps;
      how to release Safanoria (tag, then bump `version`).
- [x] VacAppKMP: `safanoria update` on its `safanoria` branch replaces the hand copy and adds
      the per-type templates. I show the diff; it is committed there only with your go-ahead.

## Design

From `v1-tooling-native-spike` (workflow at commit 657ea65, `.github/workflows/native-spike.yml`):

- Build each binary on its own runner: ubuntu-24.04 (linuxX64), windows-2022 (mingwX64),
  macos-14 (macosArm64). Cold builds take 2–4.5 min; cache `~/.konan` and Gradle caches.
- The Linux job must create the `libunistring.so` symlink before linking (see
  `v1-tooling-validate`), and the Linux binary only runs where `libunistring.so.5` exists
  (Ubuntu 24.04+). Say so in the install docs.
- Binary sizes: 5.4–5.8 MB. Windows binary is `safanoria.exe`, others `safanoria.kexe` (rename to
  `safanoria` when publishing).

## Learnings

- Kotlin's `trimMargin()` strips a leading `|` from every line, including lines that come from
  interpolated text: generating code that embeds markdown with tables corrupts them silently.
  → promoted: core/build.gradle.kts (comment in `generateEmbedded`)
- GitHub's `workflow_dispatch` only runs workflows that exist on the default branch, so a new
  workflow can't be tried by hand from a feature branch; a `push` trigger with `paths` on the
  workflow file can, and path filters don't apply to tag pushes.
  → promoted: .github/workflows/release.yml (header comment) and README "Releasing Safanoria"
- PowerShell 7's `Invoke-WebRequest` only does http(s), not `file://`.
  → promoted: install.ps1 (comment on `Fetch`)

## Work Log

- **2026-10-01** · plan · Blocked also by `cli-core` (`init`/`update` are CLI commands). Added
  binary distribution, since the CLI is a Kotlin/Native binary per OS; size S → M.
- **2026-10-02** · plan · From `v1-tooling-spec-decisions`: `init` also installs the per-type
  templates (a project's `_TEMPLATE.md` wins over the built-in ones, so without them a project
  never gets the bug template), and installed copies carry the version marker. A `Makefile`
  (`make install`) builds and installs from a checkout meanwhile.
- **2026-10-02** · status · Started. Branch `v1-tooling-install` from `v1-tooling`, worktree
  `../safanoria--v1-tooling-install`.
- **2026-10-02** · plan · Everything installed is embedded in the binary, so one version covers
  both. Managed files (skill, spec) are replaced; project-owned ones (templates, README,
  CLAUDE.md, config) are created or changed only after asking. Templates get no version marker,
  since it would be copied into every ticket. VacAppKMP's copy today: SKILL.md and SPEC.md
  both differ from this repository.
- **2026-10-02** · step 1 · `Embedded.SKILL` and `Embedded.SPEC`. The generator used
  `trimMargin`, which strips the leading `|` of every line: SPEC.md's tables would have been
  embedded broken. It now joins plain lines, fails the build on `"""` or `$$` (which a raw
  string can't hold), and a test compares every embedded file with its source byte for byte.
  `Install` in `core`: `managedFiles()` (marked), `templates()`, `ticketReadme()`,
  `claudeParagraph(dir)`, `config(dir, components)`, `installedVersion(text)`, and
  `plan(fs, root, dir)` → one `FileChange` per file: CREATE, REPLACE (managed), SAME, DIFFERS
  (project-owned: only after asking). An existing ticket README is always the project's; an
  existing CLAUDE.md mentioning the `safanoria` skill is left alone, else the paragraph is
  appended after asking. 5 tests, one shaped like VacAppKMP's hand install.
- **2026-10-02** · step 2 · `safanoria init [--dir] [--main-branch] [--component
  NAME=FILE:PROPERTY]... [--external NAME]... [--yes] [--dry-run]`, in `--root` or the working
  directory. Without components it asks (names, then each source, `external` allowed) through
  Clikt's terminal (Mordant prompts), so tests drive it with `test(stdin, inputInteractive)`.
  `applyChanges` is shared with `update`: one line per file; a project's differing file is
  changed only with `--yes` or a yes; without a terminal it is kept and the line says how to
  change it. Usage errors thrown from `run()` carry the command's context, so they show `init`'s
  usage, not the root's. 4 tests, JVM and linuxX64.
- **2026-10-02** · step 3 · `safanoria update [--yes] [--dry-run]`: same `Install.plan` and
  `applyChanges` as `init`, then `skill and spec: <before> → <after>` (`installed by hand`
  without a marker, `not installed` without the skill) or `already <version>`. 4 tests on a
  hand-installed project like VacAppKMP: skill replaced, own template kept unless agreed,
  per-type templates created, README and CLAUDE.md left alone.
- **2026-10-02** · step 4 · `release.yml`: tag `v*` → check tag = `version`, build with
  `-Prelease`, smoke-test (`version`, `validate`), publish with `SHA256SUMS`. Deviation:
  `workflow_dispatch` only runs workflows that exist on the default branch, so it can't test
  this one before it is on `main`; it also runs (without publishing) on a push to any branch
  that changes it or the install scripts. Path filters don't apply to tags, so tags always
  publish.
- **2026-10-02** · step 5 · `install.sh` (POSIX sh, curl or wget, `sha256sum` or `shasum`) and
  `install.ps1`: latest or `SAFANORIA_VERSION`, checksum checked (accepts `name` and `*name`
  lines), `~/.local/bin` or `SAFANORIA_BIN_DIR`, PATH hint (PowerShell adds it to the user
  PATH), Linux `libunistring.so.5` warning. `SAFANORIA_BASE_URL` for tests: install.sh tested
  locally (latest, a version, corrupted download refused); the workflow's `install-test` job
  runs both scripts on the three OSes against the built binaries: green. README: install,
  `init`/`update` replace the manual setup steps (part of the parent's README step), releasing
  Safanoria. Not exercised until the first `v0.1.0` tag: the `publish` job.
- **2026-10-02** · step 6 · VacAppKMP (`safanoria` branch): stopped first because it had a large
  uncommitted migration; the user committed it (`c893ee42`). Then `safanoria update`: skill and
  spec replaced (`installed by hand → 0.1.0-dev`), bug and research templates added; its
  `_TEMPLATE.md` already matched the built-in one, README and CLAUDE.md unchanged. 47 tickets
  validate. Committed there as `04408caa`, not pushed. Run `update` again after `v0.1.0`.
- **2026-10-02** · status · review. `allTests` green; this repository validates clean.
- **2026-10-02** · status · done. Merged into `v1-tooling`.
- **2026-10-02** · release · safanoria 0.1.0

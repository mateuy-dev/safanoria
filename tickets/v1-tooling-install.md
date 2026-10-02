---
id: v1-tooling-install
type: feature
title: Install the CLI, and set up or update Safanoria in a project with one command
status: in-progress
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
- [ ] `safanoria init [--dir] [--component NAME=FILE:PROPERTY]... [--external NAME]...
      [--dry-run]`: refuses when `safanoria.yaml` exists (use `update`). Without components it
      asks for them (names, then each one's version file and property, or external); without a
      terminal it needs the options. Writes `safanoria.yaml`, the ticket directory with the
      three templates and `README.md`, the skill, and the CLAUDE.md paragraph (creates CLAUDE.md,
      or asks before appending to an existing one). Validates what it wrote. Tests.
- [ ] `safanoria update [--yes] [--dry-run]`: replaces the skill and spec, prints `skill and
      spec: <before> → <after>` (`installed by hand` when there is no marker); creates missing
      templates; for a template that differs from the built-in one, asks (`--yes` replaces,
      without a terminal it is kept and reported). Tests.
- [ ] Release workflow `.github/workflows/release.yml`, on a `vX.Y.Z` tag: fails unless the
      tag equals `gradle.properties` `version`; builds the three binaries with `-Prelease`;
      publishes a GitHub release with `safanoria-linux-x64`, `safanoria-macos-arm64`,
      `safanoria-windows-x64.exe` and `SHA256SUMS`. Manual runs (`workflow_dispatch`) build and
      upload artifacts without publishing, to test it before the first tag.
- [ ] `install.sh` (Linux, macOS) and `install.ps1` (Windows): download the latest release (or
      `SAFANORIA_VERSION`) for this OS, check the checksum, put it in `~/.local/bin` (or
      `SAFANORIA_BIN_DIR`) and say if that isn't on PATH; on Linux, warn when
      `libunistring.so.5` is missing. A base-URL override lets the test install from local files.
      README: install, `init`/`update` replace the manual "Adding Safanoria to a project" steps;
      how to release Safanoria (tag, then bump `version`).
- [ ] VacAppKMP: `safanoria update` on its `safanoria` branch replaces the hand copy and adds
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

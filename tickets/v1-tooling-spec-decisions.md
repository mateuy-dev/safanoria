---
id: v1-tooling-spec-decisions
type: feature
title: Decide Safanoria versioning, attachments and per-type templates
status: review
priority: medium
size: S
created: 2026-10-01
updated: 2026-10-02
parent: v1-tooling
---

## Objective

Open points left in SPEC v1, to decide and write into SPEC.md:

- **Versioning of Safanoria itself**: spec version (`safanoria: 1`) versus tool/skill version,
  and where the tool version lives, so this repository's `safanoria` component gets a `version`
  source instead of `external: true`. `v1-tooling-install` needs this to know what it installs.
- **Attachments**: naming under `attachments/<id>/`, size limits (git history is permanent),
  how they are referenced from a ticket and shown by apps.
- **Templates per type**: whether `research` (questions as criteria) or `bug` (steps to
  reproduce) need their own template, and how a project declares them.

## Acceptance Criteria

- [x] Each point decided, with the rejected alternatives in this ticket (Design)
- [x] SPEC.md updated; spec version bumped only if SPEC §13 requires it
- [x] This repository's `safanoria` component has a `version` source
- [x] `validate` checks attachment links; `new` uses per-type templates

## Plan

Proposed decisions, one step each. Everything added is optional for old tools, so the spec
stays at version 1 (§13). Rejected alternatives go to Design as each step lands.

- [x] Versioning. Two numbers: the **spec version** (`safanoria: 1`, an integer, bumped only
      for breaking changes, §13) and the **Safanoria version** (`MAJOR.MINOR.PATCH`), one
      number for the CLI, skill, SPEC.md copy and templates, which ship together and are tagged
      `vX.Y.Z` here. It lives in `gradle.properties` `version` (plain semver: the version being
      developed; builds not made from its tag report `<version>-dev`). `safanoria version`
      already prints both. Installed copies (skill, SPEC.md) carry a `<!-- safanoria X.Y.Z -->`
      marker, so `update` (v1-tooling-install) knows what was installed. This repository:
      `safanoria` component gets `version: { file: gradle.properties, property: version }`.
      SPEC §2 example and §13, README.
- [x] Attachments: `<dir>/attachments/<id>/<file>`, owned by ticket `<id>`, referenced with
      relative links from it (`![crash](attachments/<id>/crash.png)`), which render on GitHub
      and in apps. Each file SHOULD be under 1 MB (repository history is permanent); larger
      files (videos, dumps) go elsewhere, linked by URL. §10 applies: no personal data in
      screenshots. `validate`: `attachment-missing` (error, a link to a file that isn't
      there) and `attachment-large` (warning, over 1 MB). SPEC §1, §7, §12; fixtures; README.
- [x] Templates per type: optional `<dir>/_TEMPLATE.<type>.md`, ignored like `_TEMPLATE.md`;
      `new --type T` uses it, else `_TEMPLATE.md`, else the built-in one for T, else the
      built-in default. No new sections (§7 stays uniform, so tools and `validate` don't
      change): the built-in `bug` template puts steps to reproduce, expected and actual as
      `###` subsections of Objective; `research` puts the questions as Acceptance Criteria and
      says answers go to Learnings. VacAppKMP's 18 bugs (ad-hoc headings today) are the test
      case. SPEC §1, §6.2; `templates/`; `new`; skill Create; README.

## Design

### Versioning

Decided: spec version (integer, in `safanoria.yaml`) and one Safanoria version for spec text,
skill, templates and CLI (SPEC §13). Rejected:

- **Separate versions for the skill, the templates and the CLI**: they change together (a spec
  change needs skill text, template and validator changes), and separate numbers need a
  compatibility table.
- **Semver for the spec** (`safanoria: 1.2`): a project only needs to know when its tickets may
  stop being valid; additions are ignorable by definition (§13).
- **The Safanoria version in a project's `safanoria.yaml`**: it would change on every update
  without meaning anything for the tickets; the marker in the installed copies says it.
- **`-SNAPSHOT` in `gradle.properties`**: the file is this repository's version source, which
  `release` reads as `MAJOR.MINOR.PATCH`; the `-dev` suffix is added by the build instead.

### Attachments

Decided: `<dir>/attachments/<id>/`, relative links from the owning ticket, 1 MB per file as a
SHOULD with a warning (SPEC §7.8). Rejected:

- **Git LFS**: every clone, CI job and agent sandbox needs it set up, and a missing LFS gives
  pointer files instead of an error. A project MAY still put `attachments/` under LFS itself.
- **External storage only** (links to a drive or the issue tracker): screenshots get lost or
  become private; the ticket must be readable from the repository alone.
- **Next to the ticket** (`<id>.assets/` or `<id>/`): clutters the ticket directory, which
  people browse.
- **A configurable size limit**: no project has needed one yet (VacAppKMP has no
  attachments); a fixed SHOULD with a warning can become configurable later without breaking
  anything.
- **Checking every relative link** (e.g. to source files): those break on renames that have
  nothing to do with tickets; only links into `attachments/` are the ticket's responsibility.

### Templates per type

Decided: same sections for every type; per-type guidance lives inside them (SPEC §6.2).
Lookup: `_TEMPLATE.<type>.md`, `_TEMPLATE.md`, built-in for the type (`bug`, `research`),
built-in default. Rejected:

- **Required sections per type** (e.g. `## Steps to reproduce`): every tool, the validator and
  the section order of §7 would branch on type; `###` subsections inside Objective are free
  (§7) and need nothing.
- **A `templates:` map in `safanoria.yaml`**: the file-name convention needs no config and is
  visible in the ticket directory.
- **Built-in per-type templates before the project's `_TEMPLATE.md`**: a project that
  customised `_TEMPLATE.md` (extra sections, comments) would lose that for bugs. Consequence:
  a project set up with `_TEMPLATE.md` (VacAppKMP) gets the bug template only once it has
  `_TEMPLATE.bug.md`, so `init`/`update` (`v1-tooling-install`) should install the per-type
  templates too.
- **Templates for `feature` and `maintenance`**: the default fits them; no shape was missing.

## Work Log

- **2026-10-02** · status · Started. Branch `v1-tooling-spec-decisions` from `v1-tooling`,
  worktree `../safanoria--v1-tooling-spec-decisions`.
- **2026-10-02** · plan · One step per open point, each with SPEC text and the tooling that
  follows from it. No breaking change, so spec version 1 stays.
- **2026-10-02** · out of plan · `Makefile` (`make install` to `~/.local/bin`, `PREFIX=`),
  asked for in chat to run the CLI before `v1-tooling-install` exists. Committed separately.
- **2026-10-02** · step 1 · SPEC §13: spec version vs Safanoria version, `<!-- safanoria X.Y.Z
  -->` marker on installed copies (SHOULD). `gradle.properties` `version=0.1.0` (was
  `0.1.0-SNAPSHOT`); the build checks it is `MAJOR.MINOR.PATCH` and embeds `0.1.0-dev` unless
  `-Prelease`. `safanoria.yaml`: the `safanoria` component reads that file instead of
  `external: true` (part of the parent's last Plan item, done here since it follows from the
  decision). README Development. Rejected alternatives in Design.
- **2026-10-02** · step 2 · SPEC §7.8 Attachments (new section), §1 and §12. `AttachmentRules`
  in `core`: `attachment-missing` (error) for links resolving under `<dir>/attachments/` to no
  file, skipping fenced blocks and inline code (this ticket's own Plan has such an example);
  `attachment-large` (warning) on the file itself, with its owning ticket as cause, so
  `validate <ticket>` reports it. Fixture `attachment-missing` (generator now writes extra
  files); the large case is a FakeFileSystem test, to keep a 1 MB file out of this
  repository. This repository and VacAppKMP still validate clean.
- **2026-10-02** · step 3 · SPEC §1, §6.2, §11; `templates/bug.md` and `templates/research.md`,
  embedded as `Embedded.TYPE_TEMPLATES`; `NewTicket.template(repository, type)` does the
  lookup. New editor op `replaceSectionIntro`: `--objective` replaces the Objective up to its
  first `###`, so a bug's Steps to reproduce stay (it replaced the whole section before). Skill
  Create and README. Tests: lookup order, bug ticket validates, editor op; the old
  built-in-template test now uses `maintenance` (it used `bug`). Found: VacAppKMP's
  `_TEMPLATE.md` wins over the built-in bug template (see Design).
- **2026-10-02** · status · review. `allTests` green; this repository and VacAppKMP validate
  clean. Spec version stays 1: every addition is optional (§13).

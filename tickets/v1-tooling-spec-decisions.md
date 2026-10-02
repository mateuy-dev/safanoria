---
id: v1-tooling-spec-decisions
type: feature
title: Decide Safanoria versioning, attachments and per-type templates
status: in-progress
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

- [ ] Each point decided, with the rejected alternatives in this ticket (Design)
- [ ] SPEC.md updated; spec version bumped only if SPEC §13 requires it
- [ ] This repository's `safanoria` component has a `version` source
- [ ] `validate` checks attachment links; `new` uses per-type templates

## Plan

Proposed decisions, one step each. Everything added is optional for old tools, so the spec
stays at version 1 (§13). Rejected alternatives go to Design as each step lands.

- [ ] Versioning. Two numbers: the **spec version** (`safanoria: 1`, an integer, bumped only
      for breaking changes, §13) and the **Safanoria version** (`MAJOR.MINOR.PATCH`), one
      number for the CLI, skill, SPEC.md copy and templates, which ship together and are tagged
      `vX.Y.Z` here. It lives in `gradle.properties` `version` (plain semver: the version being
      developed; builds not made from its tag report `<version>-dev`). `safanoria version`
      already prints both. Installed copies (skill, SPEC.md) carry a `<!-- safanoria X.Y.Z -->`
      marker, so `update` (v1-tooling-install) knows what was installed. This repository:
      `safanoria` component gets `version: { file: gradle.properties, property: version }`.
      SPEC §2 example and §13, README.
- [ ] Attachments: `<dir>/attachments/<id>/<file>`, owned by ticket `<id>`, referenced with
      relative links from it (`![crash](attachments/<id>/crash.png)`), which render on GitHub
      and in apps. Each file SHOULD be under 1 MB (repository history is permanent); larger
      files (videos, dumps) go elsewhere, linked by URL. §10 applies: no personal data in
      screenshots. `validate`: `attachment-missing` (error, a link to a file that isn't
      there) and `attachment-large` (warning, over 1 MB). SPEC §1, §7, §12; fixtures; README.
- [ ] Templates per type: optional `<dir>/_TEMPLATE.<type>.md`, ignored like `_TEMPLATE.md`;
      `new --type T` uses it, else `_TEMPLATE.md`, else the built-in one for T, else the
      built-in default. No new sections (§7 stays uniform, so tools and `validate` don't
      change): the built-in `bug` template puts steps to reproduce, expected and actual as
      `###` subsections of Objective; `research` puts the questions as Acceptance Criteria and
      says answers go to Learnings. VacAppKMP's 18 bugs (ad-hoc headings today) are the test
      case. SPEC §1, §6.2; `templates/`; `new`; skill Create; README.

## Work Log

- **2026-10-02** · status · Started. Branch `v1-tooling-spec-decisions` from `v1-tooling`,
  worktree `../safanoria--v1-tooling-spec-decisions`.
- **2026-10-02** · plan · One step per open point, each with SPEC text and the tooling that
  follows from it. No breaking change, so spec version 1 stays.

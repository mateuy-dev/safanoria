---
id: v1-tooling-release
type: feature
title: "`safanoria release`: stamp resolvedIn, also for external components"
status: in-progress
priority: high
size: M
created: 2026-10-01
updated: 2026-10-02
parent: v1-tooling
blockedBy: [v1-tooling-cli-core]
---

## Objective

`safanoria release <component> [<version>]` stamps `resolvedIn` as in SPEC §9, so a project's
release process can call it instead of editing tickets by hand. Without a version, it reads it
from the component's `version` source.

Also decide and specify the open point: components released from another repository
(`external: true`, e.g. VacApp's Rails server). Who runs `release`, and how it reaches the
ticket repository (e.g. CI job of the other repository opening a commit/PR here, or a manual
command with the version given).

## Acceptance Criteria

- [x] Stamps every `done` ticket with the component in `area` (or no `area` and a single
      component, §5) and no `resolvedIn.<c>`, adding the `release · <c> <v>` Work Log entry and
      setting `updated`; nothing else in the files changes
- [ ] Refuses to run off `mainBranch` (overridable), and on a malformed version
- [x] Reads the version from `{ file, property }` and `{ file, regex }` sources
- [ ] `--dry-run` lists what would be stamped
- [ ] The external-component flow is decided and written into SPEC §9

## Plan

Logic in `core` (`Release.prepare` returns the edits, writes nothing), like `new`; the CLI
checks the branch, writes, and validates. `release` does not commit: SPEC §9 says stamping
SHOULD be part of the release commit, which the project's release process makes.

- [x] Version sources in `core`: read `{ file, property }` (`key=value`, `key = value`,
      `#` comments) and `{ file, regex }` (first match, one group) relative to the root. Errors
      name the file and say what was missing. Value must be `MAJOR.MINOR.PATCH`.
- [x] `Release.prepare(repository, component, version?, today, only?)`: unknown component,
      malformed version, `external` without a version, and a version lower than one already
      stamped for that component are refused. Selects `done` tickets per §9 (not `research`,
      not `wontfix`), in id order; edits with `TicketEditor` (`setMapEntry`, `appendWorkLog`,
      `updated`). `updated` is set because §5 says "on every change" (the criterion said
      "nothing else"; changed). Given a version and a source, a mismatch is a warning, not a
      refusal (release scripts may bump the file after stamping).
- [ ] CLI `safanoria release <component> [<version>] [--dry-run] [--ticket <id>]...
      [--any-branch]`: refuses off `mainBranch` unless `--any-branch`; prints each stamped
      ticket; nothing to stamp is not an error (exit 0); refusals exit 1. Validates the written
      files. Tests on a copy of the `valid` fixture, JVM and native.
- [ ] External components, SPEC §9: stamping always runs in the ticket repository, on its
      `mainBranch`, with the version given (there is no source to read). Either a person runs it
      after the other repository releases, or that repository's release job checks out the
      ticket repository, runs `safanoria release <c> <v>`, and commits (or opens a PR).
      `done` for an external component means merged in *its* repository, which this
      repository can't see, so tickets done after that release was cut would be stamped too:
      `--ticket <id>` restricts stamping to the given tickets for that case. README usage; skill:
      release stamping is `safanoria release`.

## Work Log

- **2026-10-02** · status · Started. Branch `v1-tooling-release` from `v1-tooling`, worktree
  `../safanoria--v1-tooling-release`.
- **2026-10-02** · plan · Same shape as `new` (prepare in `core`, write in the CLI). Sets
  `updated` (§5) although the criterion said nothing else changes; criterion corrected.
  External flow: stamping runs in the ticket repository with the version given; `--ticket` for
  when `done` can't be trusted to mean "in this release".
- **2026-10-02** · step 1 · `Version` (parse, compare) and `Versions.read(repository, source)`
  → `Found` or `Failed(reason)`. Properties: `=` or `:`, spaces around, `#`/`!` comments, last
  one wins (Java properties). Regex: multiline, first match, group 1. The child-version rule
  (`child-resolved-later`) now uses `Version` instead of its own parsing. JVM quirk:
  `match.groups[1]` throws when the regex has no group (native returns null), so the count is
  checked first. Tests also read every version source of this repository and of
  `SAFANORIA_EXTRA_REPOS` (VacAppKMP's `app` and `ktor`: both read).
- **2026-10-02** · step 2 · `Release.prepare(repository, ReleaseRequest(component, version?,
  only?), today)` → `Ready(component, version, versionFromSource, stamped, files, warnings)` or
  `Refused(reason)`; all or nothing. `ktor: null` counts as not stamped (the §9 example) and is
  replaced. With `only`, every named ticket must be eligible, else a refusal saying why (not
  done, not in area, already stamped): a typo in a release command must not pass silently.
  Same version again is allowed (more tickets done since). A given version doesn't need a
  readable source. 5 tests on a VacAppKMP-shaped fake repository (`app` and `ktor` with
  sources, `rails` external); stamped files validate clean.

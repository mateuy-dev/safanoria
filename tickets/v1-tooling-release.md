---
id: v1-tooling-release
type: feature
title: "`safanoria release`: stamp resolvedIn, also for external components"
status: done
priority: high
size: M
created: 2026-10-01
updated: 2026-10-02
parent: v1-tooling
blockedBy: [v1-tooling-cli-core]
resolvedIn:
  safanoria: 0.1.0
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
- [x] Refuses to run off `mainBranch` (overridable), and on a malformed version
- [x] Reads the version from `{ file, property }` and `{ file, regex }` sources
- [x] `--dry-run` lists what would be stamped
- [x] The external-component flow is decided and written into SPEC §9

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
- [x] CLI `safanoria release <component> [<version>] [--dry-run] [--ticket <id>]...
      [--any-branch]`: refuses off `mainBranch` unless `--any-branch`; prints each stamped
      ticket; nothing to stamp is not an error (exit 0); refusals exit 1. Validates the written
      files. Tests on a copy of the `valid` fixture, JVM and native.
- [x] External components, SPEC §9: stamping always runs in the ticket repository, on its
      `mainBranch`, with the version given (there is no source to read). Either a person runs it
      after the other repository releases, or that repository's release job checks out the
      ticket repository, runs `safanoria release <c> <v>`, and commits (or opens a PR).
      `done` for an external component means merged in *its* repository, which this
      repository can't see, so tickets done after that release was cut would be stamped too:
      `--ticket <id>` restricts stamping to the given tickets for that case. README usage; skill:
      release stamping is `safanoria release`.

## Design

External components (SPEC §9): stamped in the ticket repository with the version given;
`--ticket` when `done` can't be trusted to mean "in this release". Rejected:

- **Stamping in the other repository**: the tickets aren't there; it would need its own copy or
  a remote write, and two places could stamp the same ticket.
- **A `released` status or a release-branch marker for external tickets**: a new status changes
  §6.1 for one case, and the ticket repository still couldn't see the other repository's
  branches.
- **Cut-off by date** (`--done-before`): `updated` changes for other reasons, and dates don't
  match release cuts across time zones and CI queues; naming the tickets is exact.

## Learnings

- Kotlin's `MatchResult.groups[i]` throws on the JVM for a group the regex doesn't have, but
  returns null on Native: check `groups.size` before indexing, or a common test passes on one
  target and fails on the other.
  → promoted: core/src/commonMain/…/Versions.kt (comment at the check)

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
- **2026-10-02** · step 3 · `safanoria release <component> [<version>] [--ticket <id>]...
  [--dry-run] [--any-branch]` (hidden `--date` for tests). Arguments are checked before the
  branch, so a typo is reported as such. Deviation: `--dry-run` runs on any branch, with a
  warning off `mainBranch`, since it writes nothing and previewing is its point. A detached
  HEAD (tag checkouts in CI) is "off the main branch": `--any-branch` there. Prints the
  version's origin (`from gradle.properties`) so a wrong source is visible. 3 tests on a copy
  of the `valid` fixture, JVM and linuxX64; the off-main test skips itself on `main`, where CI
  also runs.
- **2026-10-02** · step 4 · SPEC §9: external-component flow and "versions only go up" (MUST
  refuse a lower version); §2 says what `external` means. README "Releasing" (setup step 5
  points to it; `release` out of Planned, `update` in). Skill: `resolvedIn` is set by
  `safanoria release` from the release process. Rejected alternatives in Design.
- **2026-10-02** · status · review. `allTests` green; this repository validates clean.
- **2026-10-02** · status · done. Merged into `v1-tooling`.
- **2026-10-02** · release · safanoria 0.1.0

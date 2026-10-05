---
id: release-notes
type: feature
title: Version to features
status: done
priority: medium
size: S
created: 2026-10-02
updated: 2026-10-05
related: [notes-text-directly]
resolvedIn:
  safanoria: 0.2.0
---

## Objective

Given a component (e.g. app, ktor server) and a version, describe the features and fixes that version includes. Given two versions, describe the changes between them: the first version excluded, the last included.

The text is read by end users to understand what changed when they update, so it is written from the user's point of view and is non-technical. Refactors and other changes that do not matter to the user are reported only as "bug fixing".

The tickets to include can be found with `resolvedIn.<component>` (SPEC §9).

## Acceptance Criteria

- [x] `safanoria notes <component> <version>` prints the tickets with that `resolvedIn.<component>`
- [x] `safanoria notes <component> <from> <to>` prints those after `<from>` up to and including `<to>`
- [x] The skill tells how to write the user-facing text from them, internal work as "Bug fixing"

## Plan

## Work Log

- **2026-10-05** · status · started
- **2026-10-05** · decision · The CLI doesn't write the notes: `safanoria notes` prints the tickets of the range (id, type, title, parent, Objective) and the skill has the rules to write the text. Wording for end users, and telling what they notice from what they don't, needs judgement the CLI has no way to apply; `type: maintenance` alone isn't enough, since a bug fix can be internal too.
- **2026-10-05** · decision · `notes` reads the checkout, not every branch like `list`: stamps are made on `mainBranch` (§9), so the checkout has them wherever it branched from a release.
- **2026-10-05** · decision · The range ends need not be stamped versions (a user may come from a version that shipped no ticket); an empty result exits 0 and lists the stamped versions on stderr.
- **2026-10-05** · status · review
- **2026-10-05** · status · done
- **2026-10-05** · release · safanoria 0.2.0

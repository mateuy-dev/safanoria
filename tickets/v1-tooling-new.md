---
id: v1-tooling-new
type: feature
title: "`safanoria new`: create a ticket from a title"
status: done
priority: medium
size: S
created: 2026-10-01
updated: 2026-10-02
parent: v1-tooling
blockedBy: [v1-tooling-cli-core]
---

## Objective

`safanoria new "<title>"` does the mechanical part of SPEC §11 Create: suggests an id from the
title, checks it against existing tickets and branches (SPEC §3), and writes the ticket from the
project's template with `status: backlog` and today's dates. Agents and humans then fill the
Objective.

## Acceptance Criteria

- [x] Suggests a valid id from the title; `--id` overrides it
- [x] Refuses an id that exists as a ticket; warns when a branch has that name
- [x] `--parent <id>` sets `parent`, suggests a child id, and adds the child item to the
      parent's Plan
- [x] `--type`, `--priority`, `--size` options; uses `<dir>/_TEMPLATE.md` when present

## Plan

The logic is in `core` (`NewTicket`): it returns the files to write and the edits to the
parent, so the GUI can reuse it; the CLI writes them. Nothing is written when a check fails.

- [x] Id suggestion (`Ids.suggest`): like the curated ids in real tickets (VacAppKMP:
      "Andalucia Dual-File Import" → `andalucia-dual-file-import`), not a slug of the whole
      title: split camelCase, fold accents (Catalan/Spanish titles), drop filler words
      (en/es/ca: the, of, for, de, la, per, amb…), keep up to 4 words, within 40 chars.
      With a parent: `<parent>-<words>`, trimmed by words to 40. Tests with real-like titles.
- [x] `NewTicket.prepare` in `core`: template = `<dir>/_TEMPLATE.md` or the built-in one
      (`templates/ticket.md`, embedded like the schemas); fills id, title, type, priority,
      size, `status: backlog`, created/updated = today, optional `parent` and Objective text,
      with `TicketEditor` (new ops: replace a section's content, append a Plan item). Refuses:
      invalid id, existing ticket, unknown parent, parent that has a parent or is `research`
      (§8.1). Parent edit: `- [ ] \`<id>\`: <title>` after its last Plan item, `updated` = today.
- [x] CLI `safanoria new "<title>" [--id] [--parent] [--type] [--priority] [--size] [--area]
      [--objective] [--dry-run]`: `--dry-run` prints the id and files without writing (how an
      agent proposes the id before confirming it, §11); warns, without failing, when a branch
      has the id; validates the written files and reports problems. Today from
      `kotlinx-datetime` in the local time zone. Tests on the JVM and native.
- [x] Skill and README: Create uses `safanoria new --dry-run` to propose and `safanoria new`
      to create, when the CLI is installed; README usage.

## Learnings

- Clikt exits 1 on its own usage errors and its `test()` helper bypasses `main`, so exit codes
  that differ from Clikt's must be mapped in `main` and checked on the binary.
  → promoted: cli/src/commonMain/…/Main.kt (comment on `main`)
- Gradle's `latest.release` for `kotlinx-datetime` resolves to `0.8.0-0.6.x-compat`, a
  compatibility artifact, not the normal release: pin versions in the catalog.
  → ticket only

## Work Log

- **2026-10-02** · status · Started. Branch `v1-tooling-new` from `v1-tooling`, worktree
  `../safanoria--v1-tooling-new`.
- **2026-10-02** · plan · Logic in `core`, CLI writes. Id suggestion modelled on VacAppKMP's
  curated ids (short, filler words dropped), not full-title slugs. `--dry-run` supports the
  propose-then-confirm step of §11.
- **2026-10-02** · step 1 · `Ids.suggest(title, parent?)`. Not overfitted to one example:
  "Wire ApplyMovementAsSale Route" suggests `wire-apply-movement-sale` (the real id,
  `apply-movement-as-sale`, drops "Wire" by judgement); plan text corrected. Added after the
  first test run: words already in the parent id are dropped (else `herd-locations-map-input-
  herd-locations`), and Catalan `l·l` stays one word (`col·lecció` → `colleccio`). Titles with
  only filler words keep them; a leading number is dropped (ids start with a letter). 5 tests,
  JVM and linuxX64.
- **2026-10-02** · step 2 · `NewTicket.prepare(repository, request, today)` → `Ready(id,
  idSuggested, files)` or `Refused(reason)`; writes nothing. Built-in template embedded from
  `templates/ticket.md` (`Embedded.TICKET_TEMPLATE`); a project's `_TEMPLATE.md` wins, its extra
  sections and comments kept. New editor ops: `replaceSectionContent`, `appendPlanItem` (after
  the last item and its continuation lines; empty Plan gets blank lines around it). Added to the
  plan: `area` (required by §5 when there are several components; values checked), else every
  ticket `new` writes in such a project would fail `validate`. 6 tests: written files pass
  `validate` (given-files mode, so the fixture's own unrelated problem doesn't count).
- **2026-10-02** · step 3 · `safanoria new "<title>"` with `--id --parent --type --priority
  --size --area --objective --dry-run` (hidden `--date` for tests). Prints what it creates or
  updates and whether the id was suggested; warns on stderr when a branch has the id; validates
  the written files. Today: `kotlin.time.Clock` + `kotlinx-datetime` 0.8.0 `todayIn` (local
  zone); `latest.release` resolved to `0.8.0-0.6.x-compat`, so the version is pinned. 4 tests
  (JVM, linuxX64) on a copy of the `valid` fixture under `cli/build/`. Deviation: Clikt exits 1
  on its own usage errors; `main` now maps every `UsageError` to 2, as documented. Clikt's
  `test()` bypasses `main`, so that is checked on the binary (bad choice, missing argument,
  missing file, unknown command: all 2). `displayPath` shared by both commands.
- **2026-10-02** · step 4 · Skill Create: with the CLI, `new --dry-run` → confirm id → `new`,
  then requests by hand and `validate`; the manual steps stay for projects without it. Skill
  Finish also runs `safanoria validate`. README "Creating tickets"; `new` out of Planned.
- **2026-10-02** · status · review. `allTests` green; this repository validates clean.
- **2026-10-02** · status · done. Merged into `v1-tooling`.

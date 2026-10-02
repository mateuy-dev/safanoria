---
id: add-very-low-priority
type: feature
title: Add very low priority
status: in-progress
priority: medium
size: S
created: 2026-10-02
updated: 2026-10-02
---

## Objective

Add a "very low" priority below `low`. The `priority` enum (SPEC §5) is `low | medium | high | urgent`; add the new value to SPEC.md, schema/ticket.schema.json, the core parser and anything that sorts or groups by priority (board, ticket graph), with tests, and update README.md and the skill where they list priorities.

## Acceptance Criteria

- `priority: very-low` is valid: SPEC §5 lists `very-low | low | medium | high | urgent`, and
  `schema/ticket.schema.json` accepts it (a valid schema example covers it).
- The core reads it as `Priority.VERY_LOW`; `safanoria list` shows `very-low` and `safanoria new
  --priority very-low` writes it.
- Board order (`TicketGraph`): within a status, `very-low` sorts after `low`; a ticket without a
  priority still sorts last.
- The board doesn't show it as a facet (it only shows `high` and `urgent`), unchanged.
- Tests cover parsing, ordering and `new --priority very-low`; all tests pass.

## Plan

- [x] Spec and schema: add `very-low` to SPEC §5 and `schema/ticket.schema.json`; add
  `schema/examples/ticket/valid/priority-very-low.yaml`. Spelling `very-low` (kebab case, like
  `in-progress`), so `enumOf`/`text` map it to `VERY_LOW` with no special case. Spec version
  stays 1: no valid ticket becomes invalid and no field changes meaning (§13); Safanoria is
  still pre-1.0 (0.2.0-dev).
- [ ] Core: `Priority { VERY_LOW, LOW, MEDIUM, HIGH, URGENT }`. `TicketGraph` sorts by
  `-ordinal` with null as `1`, so putting it first keeps the order right without changing the
  comparator. Extend `TicketGraphTest` with a `very-low` ticket, and a frontmatter parse test.
- [ ] CLI: `new --priority` takes its choices from `Priority.entries`, so it gets `very-low`
  for free; add a `NewTest` case. README/skill don't list priority values (checked), nothing to
  change there.

## Work Log

### 2026-10-02 · status · started

Branch and worktree `../safanoria--add-very-low-priority` from `main` (no parent).

### 2026-10-02 · plan

Priority is referenced in SPEC §5, the schema, `Frontmatter.kt` (enum), `TicketGraph` (order),
`Board` (facets: high/urgent only, unaffected), `New` (choices from the enum) and `ListTickets`
(prints `text`). README and the skill don't list the values.

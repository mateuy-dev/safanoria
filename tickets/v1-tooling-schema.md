---
id: v1-tooling-schema
type: feature
title: JSON Schema for ticket frontmatter and safanoria.yaml
status: done
priority: high
size: S
created: 2026-10-01
updated: 2026-10-01
parent: v1-tooling
---

## Objective

A `schema/` directory with JSON Schemas for the ticket frontmatter (SPEC §5) and for
`safanoria.yaml` (SPEC §2). They are the machine-readable half of the spec: the CLI validates
with them, editors can use them for completion, and later apps share them.

The schemas cover only what a schema can express per file (types, enums, required fields,
id/date/version patterns). Cross-file rules (references, cycles, parent Plans, `area` against
`components`) stay in `validate`.

## Acceptance Criteria

- [x] `schema/ticket.schema.json` and `schema/safanoria.schema.json` (JSON Schema 2020-12)
- [x] Unknown fields are allowed (SPEC §2, §5)
- [x] Example valid and invalid files under `schema/examples/`, checked by a test
- [x] SPEC.md links to the schemas

## Plan

Scope rule: a constraint goes in the schema when it can be checked from that one file alone.
That includes some conditional rules (§9: `resolvedIn` only on `done`, never on
`research`/`wontfix`; §5: `who` required when `user` is null). Anything needing another file
(ids that exist, `area` ⊆ `components`, `channel` ∈ `channels`, Plan/children, cycles) stays
in `validate`.

- [x] `schema/ticket.schema.json` (draft 2020-12): required fields, enums, id pattern
      `^[a-z](-?[a-z0-9])+$` with length 3–40 (expresses "no `--`, no trailing `-`" without
      lookahead, which not every regex engine supports), `YYYY-MM-DD` dates, `MAJOR.MINOR.PATCH`
      versions, `requests` items, `refs`, `resolvedIn` rules above, `childrenMergeInto` enum.
      `additionalProperties` allowed everywhere (unknown fields are preserved, §5).
      `$id` = raw GitHub URL on `main`, so editors and tools can reference it.
- [x] `schema/safanoria.schema.json`: `safanoria: 1`, `dir`, `mainBranch`, `worktree` (must
      contain `{id}`), `components` (slug keys, at least one, each with a `version` source
      `{file, property}` or `{file, regex}`, or `external: true`), `channels`, `userRef`,
      `refs` (`{url}` with `{id}`), `learningTargets`.
- [x] Examples and test: `schema/examples/{ticket,safanoria}/{valid,invalid}/*.yaml`, one
      invalid file per rule, each starting with a `# expect: <keyword>` comment. Test runner
      `schema/check.py` (PyYAML + `jsonschema`; installed here, `pip install` in CI): validates the schemas
      against the 2020-12 metaschema, every example, and this repository's own `safanoria.yaml`
      and ticket frontmatter. YAML is loaded **without timestamp conversion** (YAML 1.1 loaders
      turn `2026-10-01` into a date; the CLI's kaml keeps strings), so dates are checked as
      strings, as the CLI will. A GitHub Actions workflow runs it on push.
      Alternative considered: test only from Kotlin in `cli-core`. Rejected for now: this ticket
      must be testable on its own, and `cli-core` will run the same examples through the
      production validator (added to its Design), so the Python runner can be dropped then if it
      is redundant.
- [x] SPEC.md §2 and §5 link to the schemas; README lists `schema/` and shows the
      `# yaml-language-server: $schema=…` line for `safanoria.yaml` (editor completion).
      Ticket frontmatter can't be wired to a schema in editors (it's inside markdown); note that.

## Learnings

- YAML 1.1 loaders (PyYAML, and js-yaml's default schema) turn `2026-10-01` into a date object,
  so a schema with `type: string` dates fails on them; kaml keeps it a string. Any tool reading
  tickets must load dates as strings. Also: an unquoted `4.3` is a number, while `4.3.0` is a
  string, so a two-part version fails with `type`, not `pattern`.
  → promoted: SPEC.md §4, schema/check.py (docstring)
- In JSON Schema 2020-12, `$ref` and its sibling keywords apply together: a `$ref` to a definition
  with `type: string` plus a sibling `type: [string, null]` still rejects null. Reusable format
  rules (`idFormat`, `versionFormat`) carry no `type`. `oneOf: [x, null]` works but reports one
  vague `oneOf` error instead of the failing rule, which is worse for mapping errors to lines.
  → promoted: schema/ticket.schema.json (`idFormat`, `versionFormat` descriptions)

## Work Log

- **2026-10-01** · status · Started. Branch `v1-tooling-schema` from `v1-tooling`, worktree
  `../safanoria--v1-tooling-schema`.
- **2026-10-01** · plan · Python test runner for now (see Plan item 3); schema includes the
  single-file conditional rules from §5 and §9.
- **2026-10-01** · step 3 · 56 examples (11 valid, 45 invalid: one per rule) and
  `schema/check.py`, which also checks this repository's `safanoria.yaml` and tickets: 69 files,
  0 failures. Each invalid example declares the keyword and JSON pointer it must fail with, so a
  file can't pass by failing for another reason. Mutation check: removing the "resolvedIn only
  on done" rule makes exactly its two examples fail. Workflow `.github/workflows/schema.yml` runs
  it on push and PR. Deviation from step 1: `parent` and `resolvedIn` use
  `type: [..., null]` plus format rules instead of `oneOf` with null, for precise errors.
- **2026-10-01** · step 4 · SPEC.md §2 and §5 link the schemas; §4 now says dates and versions
  are strings that tools must not convert (clarification, no spec version change per §13).
  README lists `schema/`, `check.py` and the `yaml-language-server` line; this repository's
  `safanoria.yaml` uses it. Learnings promoted. `schema/check.py`: 69 files, 0 failures.
- **2026-10-01** · status · review.
- **2026-10-01** · status · done. Merged into `v1-tooling`.

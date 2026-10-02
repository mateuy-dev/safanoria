---
id: v1-tooling-validate
type: feature
title: "`safanoria validate`: every SPEC §12 check"
status: in-progress
priority: high
size: M
created: 2026-10-01
updated: 2026-10-02
parent: v1-tooling
blockedBy: [v1-tooling-schema, v1-tooling-cli-core]
---

## Objective

`safanoria validate` reports every rule listed in SPEC §12, with file and line, so tickets
written by hand or by agents stay inside the format.

## Acceptance Criteria

- [ ] One test fixture per §12 rule, each reported with file and line
- [ ] Exit code 0 when valid, non-zero on errors
- [ ] `--format json` for machine-readable output (CI, editors)
- [ ] Can validate only given files (for pre-commit), still checking cross-file references
      against all tickets

## Plan

Rules live in `core` (`Validator`), so `gui-viewer` shows the same problems; the CLI only
selects files and formats output. Every problem is a `Diagnostic` with a stable kebab-case
code (`section-missing`, `ref-unknown`…), listed in the README.
Only-given-files mode (pre-commit): all tickets are loaded and checked, and a problem is reported
when its file is given **or** it is caused by a given file (e.g. a staged child set to `done`
while the parent's Plan item is unchecked is reported on the parent).

- [x] Per-ticket rules: id valid and equal to the filename; parse and schema diagnostics;
      §7 sections missing, out of order (standard sections only: extra sections like
      VacAppKMP's `## Original document` may go anywhere) or empty for the status (§7.1);
      unchecked Plan items and pending Learnings at `review`/`done`; `requests` vs quotes count;
      `channel` ∈ `channels`; `area` and `resolvedIn` keys ∈ components, `resolvedIn` keys ∈
      `area`, `area` required with more than one component. Schema errors whose meaning a rule
      states better (e.g. `resolvedIn` on a non-`done` ticket) get the rule's message.
- [x] Cross-ticket rules: duplicate id; unknown ids in `parent`, `blockedBy`, `related`, Plan
      child items and Learnings `new ticket`; `blockedBy` cycles; parents with a parent;
      `research` with children; each child exactly once in its parent's Plan, and no Plan item
      naming a ticket that isn't a child; child item checked iff child `done`/`wontfix`; a child's
      `resolvedIn` later than its parent's (per component, semver order).
- [x] Fixtures: `core/src/commonTest/fixtures/validate/<rule>/`, one mini repository per §12
      rule with an `expected.txt` (`<file>:<line> <code>`), run on JVM and native. This
      repository must validate clean; extra repositories (VacAppKMP) are reported, not asserted.
- [ ] CLI `safanoria validate [FILES…]`, `--staged` (git staged tickets and config), `--format
      text|json`; paths relative to the working directory; summary line; exit 0 valid, 1 errors,
      2 usage or no repository. Tests of the command on the JVM.
- [ ] Dogfood: CI validates this repository's tickets with the Linux binary on every push
      (a `tickets.yml` job replacing `schema.yml`); remove `schema/check.py` (the Kotlin examples
      test covers it); fix any problem it finds in our own tickets.
- [ ] README: `validate` usage, the codes table, pre-commit one-liner until `v1-tooling-hooks`.

## Design

From `v1-tooling-native-spike`:

- Per-file rules come from the JSON Schema (`v1-tooling-schema`), checked at runtime with
  OptimumCode `json-schema-validator` 0.5.5: convert the kaml node tree to `JsonElement`
  (scalars typed as in YAML 1.2: integers, booleans, else strings), validate, and map each
  error's JSON pointer back to a kaml node's line (the map's line when the property is missing).
  Cross-file rules are Kotlin.
- Linux cost, accepted by the user: the validator's `com.doist.x:normalize` links
  `-lunistring`. The build needs a `libunistring.so` symlink on the linker path
  (`linkerOpts("-L…")`), and the binary needs `libunistring.so.5` at runtime (Ubuntu 24.04+).
  If older distros must be supported, replace the validator with Kotlin rules (the schema stays
  for editors).
- Staged-files mode still needs every ticket's frontmatter (references), not every body.

From `v1-tooling-cli-core` (what `core` already provides):

- `Repository` (config, tickets by the §1 rule, lazy parsing), `Frontmatter.schemaDiagnostics()`
  and `ConfigResult.diagnostics` (schema rules with lines), `Ticket.parseDiagnostics` (YAML
  syntax, malformed Work Log entries, Learning resolutions, quote attributions), `Body`
  (sections, checklists with child items, learnings, work log, quotes), `Git.stagedFiles()`.
  `validate` adds the cross-file and structural §12 rules on top.
- The hidden `safanoria dump` (no argument) already parses and schema-checks every ticket:
  18 ms here, 32 ms for VacAppKMP's 47 tickets, 144 ms for 504 synthetic ones (Linux).
- When `validate` checks this repository's tickets in CI, remove `schema/check.py` and its
  workflow (the Kotlin `SchemaExamplesTest` covers the examples), and update the README.

## Work Log

- **2026-10-02** · status · Started. Branch `v1-tooling-validate` from `v1-tooling`, worktree
  `../safanoria--v1-tooling-validate`.
- **2026-10-02** · plan · Rules in `core`, CLI thin. Only-given-files mode also reports problems
  caused by a given file in another file. Extra sections (VacAppKMP's 45 tickets with
  `## Original document`) are not an order error. `schema/check.py` removed here.
- **2026-10-02** · step 1 · `Validator` (core) with the single-ticket rules: `id-mismatch`,
  parse and `schema-*` diagnostics, `resolved-in-not-allowed` (replaces the schema's opaque
  `type` error at `/resolvedIn`), `area-unknown-component`, `area-required`,
  `resolved-in-unknown-component`, `resolved-in-not-in-area`, `channel-unknown`,
  `requests-quotes-mismatch`, `section-missing` (at the next section's heading),
  `section-order` and `section-duplicate` (standard sections only), `section-empty`,
  `plan-unchecked`, `learning-pending`. Decision: §7.1 has no `wontfix` row; it requires
  Objective and Work Log (§6.1: "the Work Log says why"), not Acceptance Criteria or Plan.
  `Body.hasContent` ignores lines that are only an HTML comment. This repository: 0 problems;
  VacAppKMP: 0 problems.
- **2026-10-02** · step 2 · `CrossTicketRules`: `id-duplicate`, `ref-unknown` (parent,
  blockedBy, related, Plan child items, Learning new ticket), `blocked-by-cycle` (each cycle once,
  on every member, at its blockedBy entry), `parent-nested`, `research-parent`,
  `parent-plan-missing-child`, `parent-plan-duplicate-child`, `plan-item-not-child`,
  `child-check-mismatch`, `child-resolved-later` (per component, numeric semver). Each finding
  names the other tickets causing it, for only-given-files mode. Identity is the filename id;
  references to a ticket whose frontmatter id differs are reported once, as `id-mismatch`.
  This repository and VacAppKMP: 0 problems.
- **2026-10-02** · step 3 · 35 fixtures (2 valid repositories incl. extra sections, 32 rule
  cases, 1 only-given-files case), generated by `tools/make_validate_fixtures.py`, which finds
  expected lines by marker text instead of counting. The test requires the exact set of
  `<file>:<line> <code>` per case: 35/35 on JVM and linuxX64. Found and fixed: kaml's
  `yaml-syntax` messages quoted lines of the frontmatter block ("at line 5"), off by one from
  the file; they are now rewritten to file lines. kaml reports a syntax error where it notices it
  (an unclosed `[` → the next line), kept as is.

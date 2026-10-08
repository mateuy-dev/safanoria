---
id: worktree-mandatory
type: maintenance
title: Make the worktree setting mandatory
status: backlog
priority: medium
size: S
created: 2026-10-08
updated: 2026-10-08
---

## Objective

`worktree` in `safanoria.yaml` is optional today (SPEC §2). Without it, `start` switches the main checkout to the ticket's branch instead of adding a worktree, and `merge` switches it back. The user wants it mandatory: every project declares where a ticket's worktree goes, and a ticket is always worked on in its own worktree, never in the main checkout.

What this touches:

- SPEC §2 (`worktree` required, no longer `# optional`), and §11 steps 2 and 3, which say "if configured".
- `schema/safanoria.schema.json`: `worktree` in `required` (it already must contain `{id}`).
- `Config`: `worktree` non-null; a `safanoria.yaml` without it is a configuration error with a message that says what to add. `init` writes it in new projects.
- The no-worktree path goes away: `Start.switchCheckout` and the nullable `Ready.worktree`, `start --no-switch`, the `--print-path` fallback to the checkout root, the GUI's "else this checkout" branch in `TicketStore` and the "when the project has one" wording in `TicketScreen`, and the README passages on working without a `worktree` setting.
- `Land.cleanUp` handles a ticket branch checked out in the main checkout ("no `worktree` setting"). That can still happen by hand (`git switch <id>`), so decide whether it stays as it is.

Existing projects without the key stop loading until they add it. Decide whether that is acceptable within spec version 1, or needs a note for those upgrading.

## Acceptance Criteria

## Work Log


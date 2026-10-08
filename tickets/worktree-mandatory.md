---
id: worktree-mandatory
type: maintenance
title: Make the worktree setting mandatory
status: review
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
- `Land.cleanUp` handles a ticket branch checked out in the main checkout ("no `worktree` setting"). That handling goes too: a ticket branch in the main checkout is no longer a supported state, even though `git switch <id>` can still produce it by hand.

Existing projects without the key stop loading until they add it. That is acceptable within spec version 1, with no upgrade note: Safanoria is used in three projects and all of them define `worktree`.

## Acceptance Criteria

- [x] SPEC §2 and the schema require `worktree`; §11 no longer says "if configured".
- [x] A `safanoria.yaml` without `worktree` isn't loaded, `validate` reports it with a message that says what to add, and `start` refuses with that message.
- [x] `init` writes `worktree` in new projects.
- [x] `start` always adds the worktree and never touches the main checkout; `--no-switch` is gone and `--print-path` prints only the worktree.
- [x] `merge` no longer switches a main checkout that is on the ticket's branch: it merges and keeps the branch, saying why.

## Work Log

- **2026-10-08** · status · started
- **2026-10-08** · decision · A missing `worktree` makes `ConfigLoader` return no config with one `config-worktree` diagnostic, which replaces the schema's "missing required properties: [worktree]" because that one doesn't say what to add. Other schema errors still load the config with defaults; this key has no default to fall back to.
- **2026-10-08** · decision · Only `start` refuses on a config that didn't load. The other commands keep running on the defaults, as they already do for a `safanoria.yaml` with a syntax error, and `validate` reports the problem. `Repository.config` therefore still needs a stand-in value (`../<directory>--{id}`), which nothing uses to create a worktree. Refusing every command was left out as a wider change than this ticket.
- **2026-10-08** · decision · `init` writes `worktree: ../<project directory>--{id}`, the convention the three projects use, and takes `--worktree` to choose another place. It doesn't ask: the default suits a sibling directory in any project.
- **2026-10-08** · decision · When the ticket's branch is checked out in the main checkout (by hand), `merge` still merges and sets `done`, and keeps the branch with a line saying where it is checked out. Refusing the merge was the alternative; keeping it mirrors what clean-up already does for a worktree it can't remove.
- **2026-10-08** · status · review

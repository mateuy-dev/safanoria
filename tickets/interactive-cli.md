---
id: interactive-cli
type: feature
title: Interactive cli
status: done
priority: medium
size: S
created: 2026-10-02
updated: 2026-10-05
related: [cli-all-actions]
resolvedIn:
  safanoria: 0.2.0
---

## Objective

Make the `safanoria` CLI interactive, so a person in a terminal can work with tickets without remembering flags. Two forms, to be decided when planning (one, the other, or both):

- **Prompts**: commands ask for missing values (title, type, parent, id…) and offer choices, instead of requiring every flag.
- **Full-screen TUI**: a terminal app to browse the board, open tickets and run actions from the keyboard.

Scripts and agents must keep working: the non-interactive behaviour stays the default when stdin is not a terminal.

## Acceptance Criteria

- [x] `safanoria` without a command asks which one to run (only `init` outside a repository).
- [x] `new` without a title asks for title, type and id, optionally priority, size, parent and objective, and off `mainBranch` whether the ticket goes there; with a title it asks only for a missing `area`.
- [x] `start`, `finish` and `release` without an id or component offer a list of the tickets or components that fit.
- [x] Without a terminal on stdin and stdout nothing is asked: a missing value is an error, and the bare command prints the help, as before.
- [x] `init` and `update` ask through the same prompts: same terminal check, same lists, same cancelling.
- [x] Ctrl-C cancels without changing anything.

## Plan

## Learnings

- Mordant 3.0.2's interactive lists ignore a lone Esc on Linux native (in a pty it waits for another byte); Ctrl-C cancels them.
  → promoted: code comment (`cli/.../Prompts.kt`)

## Work Log

- **2026-10-02** · status · started
- **2026-10-03** · decision · Prompts, not a full-screen TUI: commands ask for missing values and offer choices. A TUI is a separate app to build and maintain, and prompts cover the goal (no flags to remember).
- **2026-10-03** · decision · Prompts only when stdin and stdout are both terminals, behind a `Prompts` interface that tests replace with scripted answers: Clikt's `test()` can't drive Mordant's raw-mode lists.
- **2026-10-03** · decision · `new` asks only when the title is missing; with a title, flags and defaults decide as in a script (except a missing `area`, which would be an error anyway). Keeps typed commands predictable.
- **2026-10-03** · decision · The root menu runs the chosen command by parsing the root again with it: a subcommand parsed on its own has no parent context (`--root`, prompts).
- **2026-10-03** · decision · `init` and `update` keep their own questions (`Setup.kt`, stdin-only check); moving them onto `Prompts` isn't needed for this ticket.
- **2026-10-05** · decision · `init` and `update` moved onto `Prompts` after all, so every command asks the same way: they checked only stdin, took end of input as "no", and couldn't be scripted in tests. `init` now offers file or external as a list instead of a typed 'external'.
- **2026-10-05** · status · review
- **2026-10-05** · status · done
- **2026-10-05** · release · safanoria 0.2.0

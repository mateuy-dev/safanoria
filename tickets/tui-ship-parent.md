---
id: tui-ship-parent
type: feature
title: Ship a parent from the TUI and the desktop app
status: backlog
priority: medium
size: M
related: [parent-partial-release]
created: 2026-11-04
updated: 2026-11-04
---

## Objective

Let the terminal app and the desktop app do what `safanoria-cli ship <parent>` does (SPEC §8.1, partial release of a parent): merge an in-progress parent's branch, with the children done there, into mainBranch, log it in the parent's Work Log, optionally stamp resolvedIn, and merge mainBranch back into the parent's branch so the remaining children keep it as their target.

Today only the CLI can (ticket parent-partial-release added core `Ship.prepare/plan/perform` and `ShipCommand`). The TUI and the desktop app already merge and reopen through `views/.../TicketStore.kt` (Land, Reopen); ship should go the same way: a ship action in TicketStore built on `Ship`, a key in the TUI and an action in the desktop app, offered only for a parent in-progress whose `Ship.prepare` is Ready (a child done on its branch and not yet on main), showing the refusal reason otherwise.

Stamping is optional: ask whether to stamp, which component(s) (default: all components, or the only one) and the version (default from the component's version source; required for external components). Shipping without stamping must stay possible. Confirm before doing it, saying that everything on the parent's branch ships, its own unfinished work too, and that nothing is pushed. Follow the existing merge action's patterns (confirmation, error display, refresh after).

## Acceptance Criteria

## Work Log


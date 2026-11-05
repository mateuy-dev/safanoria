---
id: tui-ship-parent
type: feature
title: Ship a parent from the TUI and the desktop app
status: done
priority: medium
size: M
related: [parent-partial-release]
created: 2026-11-04
updated: 2026-11-05
resolvedIn:
  safanoria: 0.10.0
---

## Objective

Let the terminal app and the desktop app do what `safanoria-cli ship <parent>` does (SPEC §8.1, partial release of a parent): merge an in-progress parent's branch, with the children done there, into mainBranch, log it in the parent's Work Log, optionally stamp resolvedIn, and merge mainBranch back into the parent's branch so the remaining children keep it as their target.

Today only the CLI can (ticket parent-partial-release added core `Ship.prepare/plan/perform` and `ShipCommand`). The TUI and the desktop app already merge and reopen through `views/.../TicketStore.kt` (Land, Reopen); ship should go the same way: a ship action in TicketStore built on `Ship`, a key in the TUI and an action in the desktop app, offered only for a parent in-progress whose `Ship.prepare` is Ready (a child done on its branch and not yet on main), showing the refusal reason otherwise.

Stamping is optional: ask whether to stamp, which component(s) (default: all components, or the only one) and the version (default from the component's version source; required for external components). Shipping without stamping must stay possible. Confirm before doing it, saying that everything on the parent's branch ships, its own unfinished work too, and that nothing is pushed. Follow the existing merge action's patterns (confirmation, error display, refresh after).

## Acceptance Criteria

- [x] `TicketStore` ships a parent through core `Ship` (prepare, plan, perform) and refreshes after, as merge does.
- [x] The TUI (`S`) and the desktop app (Ship to main branch) offer it on an in-progress parent with a child done on its branch, and show `Ship.prepare`'s refusal reason when there is nothing to ship.
- [x] The confirmation says everything on the branch ships, its own unfinished work too, and nothing is pushed; shipping without stamping is the default.
- [x] Stamping asks the components (all chosen to start with) and versions (as their sources read them on the merged branches; required for external ones).

## Work Log

- **2026-11-04** · status · started
- **2026-11-04** · decision · The action shows from the snapshot alone (in-progress parent on its branch, children not `childrenMergeInto: main`, a child done there); `Ship.prepare` runs when it is asked, not on every refresh, since it needs git (merge-tree, worktree status). A refusal (e.g. already on main) is shown then.
- **2026-11-04** · decision · Default versions come from the new core `Ship.versions`, read on the merged tree as `Ship.plan` would; a version left unchanged is sent as null so `Release` reads it from source itself. The TUI key is `S` (`s` is start).
- **2026-11-04** · status · review
- **2026-11-04** · status · done
- **2026-11-05** · release · safanoria 0.10.0

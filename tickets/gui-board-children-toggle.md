---
id: gui-board-children-toggle
type: feature
title: "GUI: toggle to show the child tickets on the general board"
status: done
priority: medium
size: S
area: [safanoria]
created: 2026-10-14
updated: 2026-10-20
related: [gui-parent-children-board]
requests:
  - user: enelrecord-apps
    channel: other
    date: 2026-10-14
resolvedIn:
  safanoria: 0.6.0
---

## Objective

The general board lists every ticket, children included, each with a ↑ parent line. In a project with a big parent (enelrecord-apps: cerimonies-printable and its 16 children) the children crowd the columns and hide the top-level work. Since gui-parent-children-board a parent's screen shows a board of its children, and the parent's card shows their progress, so the general board doesn't need them. Hide child tickets on the general board by default, with a toggle next to the filters to show them.

## User Requests

> When I run safanoria in @~/projects/clients/enelrecord/enelrecord-apps/ in the top project I see child tickets. Why?
— enelrecord-apps · other · 2026-10-14

## Acceptance Criteria

- [x] The general board hides child tickets by default; the count in the title says how many are shown.
- [x] A toggle next to the filters shows them again, with their ↑ parent line.
- [x] A parent's screen still shows its children board.

## Work Log

- **2026-10-14** · status · started
- **2026-10-14** · decision · The toggle is a view flag in the GUI's BoardViewModel, not a field of core's `TicketFilter`: the CLI's `list` shares that filter and must keep listing children. Clear leaves it, as it isn't a filter.
- **2026-10-14** · decision · Only children whose parent is in the project are hidden: a child with a missing parent would otherwise be unreachable from the board.
- **2026-10-14** · status · review
- **2026-10-14** · status · done
- **2026-10-20** · release · safanoria 0.6.0

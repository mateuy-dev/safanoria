---
id: gui-board-children-toggle
type: feature
title: "GUI: toggle to show the child tickets on the general board"
status: in-progress
priority: medium
size: S
area: [safanoria]
created: 2026-10-14
updated: 2026-10-14
related: [gui-parent-children-board]
requests:
  - user: enelrecord-apps
    channel: other
    date: 2026-10-14
---

## Objective

The general board lists every ticket, children included, each with a ↑ parent line. In a project with a big parent (enelrecord-apps: cerimonies-printable and its 16 children) the children crowd the columns and hide the top-level work. Since gui-parent-children-board a parent's screen shows a board of its children, and the parent's card shows their progress, so the general board doesn't need them. Hide child tickets on the general board by default, with a toggle next to the filters to show them.

## User Requests

> When I run safanoria in @~/projects/clients/enelrecord/enelrecord-apps/ in the top project I see child tickets. Why?
— enelrecord-apps · other · 2026-10-14

## Acceptance Criteria

- [ ] The general board hides child tickets by default; the count in the title says how many are shown.
- [ ] A toggle next to the filters shows them again, with their ↑ parent line.
- [ ] A parent's screen still shows its children board.

## Work Log

- **2026-10-14** · status · started

---
id: tui-children-arrow-navigation
type: bug
title: "TUI: arrows don't navigate the children board"
status: backlog
priority: medium
size: S
created: 2026-11-03
updated: 2026-11-03
requests:
  - user: null
    who: Project maintainer
    channel: other
    date: 2026-11-03
---

## Objective

In the terminal app (`safanoria-cli tui`), a parent ticket's page shows its children as a board (`drawChildren` in `tui/.../TicketPage.kt`). Once focus is on a child card (via Tab), the arrow keys don't move between the children: `TicketPage.key` always maps ArrowUp/ArrowDown (and `k`/`j`) to scrolling the page and ArrowLeft (`h`) to going back, so only Tab/Shift+Tab reach the other cards. The Board screen lets you move between columns and cards with arrows or `hjkl`, and the children board should behave the same.

Wanted: while a child card is focused, arrows and `hjkl` move between the child cards, by column (left/right) and within a column (up/down), as on the Board screen, and Enter opens the focused child. When focus isn't on a child card, the keys keep their current meaning (scroll, back). Decide how to leave the children board (e.g. left from the first column goes back as today, or Esc/Tab) and keep the scroll following the focused card. Reuse the Board screen's navigation where possible rather than writing a second one.

The user's report was short ("when inside a child, I can't navigate with arrows"). If, while working, it turns out to mean the page of a child ticket itself (arrows not scrolling there), fix that instead and log the reading as a decision.

### Steps to reproduce

1. `safanoria-cli tui`, open a parent ticket with children (e.g. `choose-session-agent`).
2. Tab until a child card is focused.
3. Press the arrow keys.

### Expected

Focus moves between the child cards, as on the Board screen.

### Actual

Up/Down scroll the page and Left goes back to the previous screen; focus stays on the same card.

## User Requests

> in the tui, when inside a child, I can't navigate with arrows
— Project maintainer · other · 2026-11-03

## Acceptance Criteria

## Work Log

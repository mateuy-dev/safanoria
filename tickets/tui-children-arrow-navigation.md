---
id: tui-children-arrow-navigation
type: bug
title: "TUI: arrows don't navigate the children board"
status: done
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

- [x] On a focused child card, arrows and `hjkl` move between the cards: left/right across columns (skipping those without children), up/down within a column; Enter opens the focused child.
- [x] Off the cards, the keys keep their meaning (scroll, back); the scroll follows the focused card.
- [x] The Board screen and the children board share the same step logic (`boardStep`).

## Work Log

- **2026-11-03** · status · started
- **2026-11-03** · decision · Read the report as the children board on a parent's page, as the Objective describes, not a child ticket's own page (whose arrows already scroll).
- **2026-11-03** · decision · Leaving the board: where an arrow has no card to go to it keeps its usual meaning, so left from the first column goes back and up/down at a column's ends scroll the page; Tab still leaves too. Moving across columns keeps the row, clamped to the shorter column.
- **2026-11-03** · status · review
- **2026-11-03** · status · done

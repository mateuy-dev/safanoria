---
id: interactive-menus-make-descriptions
type: feature
title: "Interactive menus: make descriptions less prominent"
status: done
priority: medium
size: S
created: 2026-10-05
updated: 2026-10-05
resolvedIn:
  safanoria: 0.2.0
---

## Objective

The interactive lists (the `safanoria` action menu and the other `Prompts.choose` / `chooseMany` lists in `cli/.../Prompts.kt`, built on Mordant's `interactiveSelectList`) are very difficult to read: each entry's description is rendered as prominently as its label, so the labels, which are what the person is scanning for and choosing between, don't stand out.

Make the descriptions visually secondary (e.g. dim/gray, via the list's description style) so the labels read first and the description is supporting detail. While there, check the rest of the list's readability (the cursor/selected entry is clearly distinguishable, the title stands apart from the entries) and that it stays legible on light and dark terminal themes and without colour (`NO_COLOR`, dumb terminals).

## Acceptance Criteria

## Work Log

- **2026-10-05** · status · started
- **2026-10-05** · decision · Descriptions go beside the label, dim, in an aligned column (one line per entry) instead of Mordant's layout (description on its own line under the label). Dimming alone wasn't enough: without colour (`NO_COLOR`) a label and its description were two identical-looking lines at the same indent. The column separates them with no styling at all, and the list is half as tall.
- **2026-10-05** · decision · Mordant 3.0.2 has no description style, so the description is part of the entry title (`choiceLines`), styled with `dim`: it keeps the terminal's own foreground, so it reads on light and dark themes. Descriptions that don't fit the terminal width are cut with `…` rather than wrapped, to keep one line per entry. Cursor (`❯` + green label) and title (bold, unindented) were already distinct, also without colour; left as they are.
- **2026-10-05** · status · review
- **2026-10-05** · status · done
- **2026-10-05** · release · safanoria 0.2.0

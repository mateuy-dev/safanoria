---
id: tui
type: feature
title: "Console app (TUI) similar to the GUI, with keyboard and mouse"
status: review
priority: medium
size: L
area: [safanoria]
created: 2026-10-21
updated: 2026-10-21
---

## Objective

Safanoria has a desktop GUI (gui module: board, ticket screen, versions screen, with actions like start, finish, merge) and a CLI of one-shot commands, but nothing in between for working from a terminal. Build a nice terminal (TUI) app similar to the GUI: the same main views (board with status columns and filters, ticket detail, versions) and the same actions on tickets, usable both with the keyboard (shortcuts, navigation) and with the mouse (clicking cards, buttons and tabs). It should reuse core for reading and changing tickets, as the GUI and CLI do, so its behaviour matches theirs. Useful where the GUI isn't (over SSH, inside Herdr/tmux, without a desktop).

## Acceptance Criteria

- [x] Board (status columns, collapsible, filter chips, children toggle), ticket screen (facts, links, problems, children board, markdown body) and versions screen (per component) show what the GUI's do, computed by the same code (`views`).
- [x] The GUI's actions on a ticket (start, start in Claude, create worktree, open a shell, finish, merge, back to in progress), confirmed like in the GUI, with the reason asked when reopening.
- [x] Everything works by keyboard (arrows/hjkl, Enter, Tab, Esc, action keys, `?` help) and by mouse (click cards, chips, buttons, tabs; wheel scrolls).
- [x] Works without a desktop: a shell or Claude opens in a Herdr tab, a tmux window, or the same terminal with the app back when it exits.
- [x] Ships in the native `safanoria-cli` for every target (`safanoria-cli tui`, and `safanoria-cli` alone on a terminal in a project).

## Learnings

- Mordant 3.0.2 reads the mouse wrong on POSIX: it keeps the +32 of the X10 button byte, so the wheel comes as left/right clicks, and it only asks for UTF-8 (1005) reports, not SGR (1006). Raw-mode bytes parsed by our own code work; on Windows its console events are right.
  → promoted: code comment (`tui/.../Input.kt`, `InputParser`)

## Work Log

- **2026-10-21** · status · started
- **2026-10-21** · decision · Built on Mordant 3.0.2 (already in the CLI through Clikt), drawing the screens on our own cell buffer: it reads keys and mouse events in raw mode on JVM and every native target, so the TUI ships inside the native `safanoria-cli` (`safanoria-cli tui`, and plain `safanoria-cli` on a terminal in a repository). Rejected Mosaic (no mouse events), Lanterna (JVM only) and Kotter (no mouse).
- **2026-10-21** · decision · The GUI's UI-free state (board columns and cards, ticket and versions view state, `TicketStore` with start/finish/merge/reopen) moved from `gui` to a new multiplatform `views` module that both apps use, so they can't drift apart. `java.time` became kotlinx-datetime for native.
- **2026-10-21** · decision · On POSIX the TUI parses the raw input bytes itself (SGR mouse, keys, a lone Esc), with Mordant only for raw mode: Mordant's parser turns the wheel into clicks (see Learnings). Windows keeps Mordant's console events.
- **2026-10-21** · decision · Where the GUI opens a desktop terminal, the TUI opens a new Herdr tab (a workspace for Claude on L/XL tickets, as `start --herdr`), a new tmux window, or else runs the shell or Claude in the same terminal and comes back when it exits. `Herdr.open` takes a null prompt for a tab with only a shell.
- **2026-10-21** · decision · `safanoria-cli` alone on a terminal in a project opens the TUI instead of the command menu; outside a project it still offers `init`.
- **2026-10-21** · status · review

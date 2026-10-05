---
id: gui-viewer
type: feature
title: Compose Desktop app to view tickets
status: review
priority: low
size: L
created: 2026-10-01
updated: 2026-10-05
---

## Objective

A Compose Desktop (JVM) app to browse a project's tickets: board by status, parents with their
children and progress, blocked tickets, and a readable ticket view. It reuses the KMP `core`
module from `v1-tooling` (parser, validator, operations), so it never re-implements the format.
Editing comes later, through the same targeted edits the CLI uses.

It also acts on a ticket from its screen: **Start** on a backlog or ready ticket (as
`safanoria start`), which then opens a terminal in the new workspace, and **Open terminal** on a
ticket in progress or in review, in the directory where its branch is checked out.

Out of scope for v1 (see `v1-tooling`).

## Acceptance Criteria

- [x] `./gradlew :gui:run --args=<path>` opens the project containing the path
- [x] The board has a column per status with every ticket, read from every local branch
- [x] Parents show their progress, children their parent, blocked tickets their open blockers
- [x] The board filters by type, area and blocked
- [x] A ticket's body is rendered markdown, with its fields and links to related tickets
- [x] `validate` problems show on the card and in the ticket
- [x] Start, Open terminal and Finish do what the CLI does, through `core`
- [x] The README says how to run and use it
- [ ] Tried by hand: opening a card, Back, Refresh, the filters, collapsing a column, Start,
      Open terminal and Finish (so far checked with tests and by looking at each screen)

## Plan

## Work Log

- **2026-10-05** · status · started
- **2026-10-05** · decision · Spike: a JVM-only `gui` module (Compose Multiplatform 1.12.1,
  Navigation 3 1.1.2, androidx lifecycle 2.11.0) builds with Kotlin 2.4.20 and Gradle 9.3.0 and
  shows this repository's tickets through `core` unchanged. Run: `./gradlew :gui:run --args=<path>`.
- **2026-10-05** · decision · Architecture, asked for by the user so more screens and actions
  (start, finish) fit later: one `TicketStore` holds the tickets as last read (every branch, as
  `list` does) and will hold the operations; a ViewModel per screen maps it to a ViewState; a
  stateless `...Content` composable draws it. Navigation 3 with one ViewModel store per back
  stack entry. Dependencies are passed by hand (`AppContainer`); no DI library for two screens.
  The back stack is a plain state list of routes, not `rememberNavBackStack`: a desktop app has
  no process death to restore from, so routes need no serialization.
- **2026-10-05** · decision · The kanban shows every ticket as its own card in its status column,
  children too (with their parent's id), instead of nesting children under the parent as the
  markdown board does: a child's status often differs from its parent's.
- **2026-10-05** · decision · The ticket body is drawn by `multiplatform-markdown-renderer`
  (mikepenz, 0.45.0) rather than our own renderer: tickets use tables, code and task lists.
  Known defect: text in angle brackets inside inline code is dropped (`attachments/<id>/` shows
  as `attachments//`).
- **2026-10-05** · decision · Board columns share the window's width and Done and Won't fix start
  collapsed (asked by the user): closed work is most of the tickets. Which columns are collapsed
  and the filter are ViewModel state, so they last while the board is on the back stack, not
  across runs.
- **2026-10-05** · decision · Filters reuse core's `TicketFilter` (type, area, blocked); area
  chips come from the areas tickets have, so a project without areas shows none.
- **2026-10-05** · decision · Problems come from `Validator`, which checks this checkout's files,
  while the board shows each ticket's real copy, maybe from another branch. A problem is shown
  on a ticket only when the copy shown is the checkout's; for other tickets it is dropped rather
  than shown against text it wasn't found in. So a ticket read from another branch shows no
  problems, even if its own copy has some.
- **2026-10-05** · decision · Board columns go left to right as a ticket moves: Backlog, Ready,
  In progress, Review, Done, Won't fix. The user asked for Backlog first; the rest follows the
  workflow. Core's `STATUS_ORDER` (work in hand first) stays for `list` and the markdown board.
- **2026-10-05** · decision · Objective widened at the user's request: Start and Open terminal
  on the ticket screen. Start is offered for `ready` tickets too, since `safanoria start`
  accepts both.
- **2026-10-05** · decision · The steps of starting (branch with the started ticket committed,
  worktree, switching the checkout) moved from the CLI's `StartCommand` into core's `Start`
  (`begin`, `addWorktree`, `switchCheckout`), so the app starts a ticket exactly as the CLI
  does without needing the CLI installed. The command keeps its messages and options.
- **2026-10-05** · decision · Open terminal goes where branch `<id>` is checked out
  (`BranchView.open(...).worktree`); a branch checked out nowhere gets a message, not a new
  worktree. On Linux the terminal is `$TERMINAL`, else the first usual one on the PATH
  (`x-terminal-emulator` first), run with the workspace as its working directory. Only a
  terminal is opened; no agent session is launched in it.
- **2026-10-05** · decision · Text in angle brackets is kept by handling the parser's `HTML_TAG`
  tokens in the renderer's annotator: tickets write placeholders as `<id>` and have no HTML to
  draw. Fixes the defect noted above.
- **2026-10-05** · decision · Finish (to `review`) added on in-progress tickets; its steps moved
  from the CLI's `FinishCommand` into core's `Finish.perform`, as with Start. `done` isn't
  offered: it means merged, which the app doesn't do.
- **2026-10-05** · decision · The tickets are read again when the window gets the focus back,
  not by watching files: changes on other branches are git refs, which a watcher on the
  ticket directory wouldn't see.
- **2026-10-05** · decision · Start and Finish ask for confirmation first (asked by the user):
  one click would otherwise create a branch and worktree or make a commit. Open terminal
  changes nothing and doesn't ask.
- **2026-10-05** · decision · The user's icons (`design/`, SVG) are the window icon and sit in
  the board's top bar, read from `design/` as a resource directory rather than copied into
  `gui/`. The app uses `safanoria-icon-small.svg`, drawn for small sizes; the detailed
  `safanoria-icon.svg` is for large ones (an installer, later).
- **2026-10-05** · status · review

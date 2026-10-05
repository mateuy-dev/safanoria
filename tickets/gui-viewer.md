---
id: gui-viewer
type: feature
title: Compose Desktop app to view tickets
status: in-progress
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

Out of scope for v1 (see `v1-tooling`).

## Acceptance Criteria

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

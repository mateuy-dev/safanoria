---
id: new-crashes-ticket-directory
type: bug
title: "new crashes when the ticket directory doesn't exist"
status: in-progress
priority: medium
size: S
created: 2026-10-06
updated: 2026-10-29
---

## Objective

In a repository whose `safanoria.yaml` is valid but whose ticket directory (`dir`, e.g. `tickets/`) doesn't exist yet, `safanoria-cli new "<title>"` aborts with an uncaught `okio.FileNotFoundException: No such file or directory` and a native stack trace (core dumped) instead of creating the ticket. `new` writes the file without creating its parent directory. With `--on <branch>` it works, since the commit is built from git objects. Found while reproducing issue-branch-fails-validate in a scratch repository (0.3.0-dev). Expected: the directory is created, as `init` would have done.

### Steps to reproduce

1. `git init` a repository with a valid `safanoria.yaml` and no `tickets/` directory.
2. `safanoria-cli new "First thing"`.

### Expected

`tickets/first-thing.md` is created (and the directory with it).

### Actual

`okio.FileNotFoundException: No such file or directory`, a stack trace, exit by abort.

## Acceptance Criteria

- [x] `new` creates a missing default ticket directory and writes a valid ticket.
- [x] `new` creates missing parent directories for a nested custom `dir`.
- [x] `new --dry-run` leaves missing ticket directories absent.

## Work Log

- **2026-10-29** · status · started
- **2026-10-29** · decision · Create parent directories when writing the prepared files in the CLI, so preparation and dry runs remain free of filesystem changes.

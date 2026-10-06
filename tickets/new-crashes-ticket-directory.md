---
id: new-crashes-ticket-directory
type: bug
title: "new crashes when the ticket directory doesn't exist"
status: backlog
priority: medium
size: S
created: 2026-10-06
updated: 2026-10-06
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

### Steps to reproduce

1.

### Expected

### Actual

<!-- Error messages, refs (e.g. sentry) and the version where it happens. Screenshots and logs: attachments/<id>/ (SPEC §7.8), without personal data. -->

## Acceptance Criteria

## Work Log

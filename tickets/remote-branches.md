---
id: remote-branches
type: feature
title: Read remote branches to see tickets being worked on another computer
status: done
priority: medium
size: S
created: 2026-10-20
updated: 2026-10-20
---

## Objective

A ticket started on another computer has its real state (status, Plan, Work Log) only on branch `<id>` there. Once that branch is pushed, this computer only has it as a remote-tracking branch (`origin/<id>`), so the board, `list`/status and the validator still show the `mainBranch` copy (`backlog`) and the work in progress elsewhere is invisible; ids taken only on a remote branch can also be reused.

Read remote-tracking branches as SPEC §14.1 already allows ("Tools MAY include remote-tracking branches; a local branch wins over a remote one with the same name"), so a ticket being done on another computer shows its real status and is marked as remote. Decide whether to fetch (and how often) or only read what was last fetched, and whether starting a ticket that is already in progress remotely should warn or refuse.

## Acceptance Criteria

- [x] `list`, `board` and the app read remote-tracking branches by default, as last fetched; `--local` turns it off.
- [x] A copy read from a remote ticket branch (no local branch `<id>`) is marked `on origin/<id>` (`remote` in `list --format json`); `mainBranch`'s copy never is.
- [x] `start` refuses a ticket started on another computer and prints how to check it out; `finish`, `merge` and `reopen` refuse it too.
- [x] The app has a Fetch button (`git fetch --all --prune`), and a Create worktree action for such a ticket (local branch tracking `origin/<id>`).

## Work Log

- **2026-10-20** · status · started
- **2026-10-20** · decision · Remote-tracking branches are read by default (`--remote` replaced by `--local`): reading refs is cheap and `validate` already did it. Nothing fetches implicitly (network, credentials, slowness); only the app's Fetch button does.
- **2026-10-20** · decision · `start` refuses a ticket on `origin/<id>`, like an existing local branch, instead of warning: starting it again makes a second branch that diverges. The app offers Create worktree instead; the CLI prints the `git worktree add --track` command (a CLI command for it can follow if wanted).
- **2026-10-20** · decision · The mark is computed on read (`Ticket.remote`), not stored. Only ticket branches are marked, never `mainBranch`: a CI clone with only `origin/main` would mark everything.
- **2026-10-20** · decision · A remote branch merged into `origin/main` counts as merged even before `main` is pulled, so it isn't read and doesn't show a stale in-progress; the ticket shows `main`'s copy until it is pulled.
- **2026-10-20** · status · review
- **2026-10-20** · status · done

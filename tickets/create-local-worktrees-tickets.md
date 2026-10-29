---
id: create-local-worktrees-tickets
type: feature
title: Create local worktrees for tickets started on another computer
status: in-progress
priority: medium
size: S
created: 2026-10-29
updated: 2026-10-29
requests:
  - user: null
    who: Project maintainer
    channel: other
    date: 2026-10-29
---

## Objective

After pulling the main branch and fetching ticket branches on another computer, a user may have tickets that were already started elsewhere but have no local worktree. Allow the user to create a local worktree for such a ticket from its existing branch, so they can continue its work on this computer. Reuse the existing ticket branch and its current work rather than starting the ticket again, and use the configured worktree path. Handle branches available only as remote-tracking branches as well as existing local branches.

## User Requests

> create a new ticket: when a computer pulls master he may donwload tickets with its branch. The user should be able to create those worktrees form started tickets.
— Project maintainer · other · 2026-10-29

## Acceptance Criteria

- [x] A user can create a local worktree for an already-started ticket whose branch is available locally or as a remote-tracking branch.
- [x] The worktree uses the configured path and preserves the existing branch history, ticket status, and work log.
- [x] Repeating the action when a local worktree already exists does not create a duplicate or overwrite existing work.

## Work Log

- **2026-10-29** · status · started
- **2026-10-29** · decision · Expose `checkout <id>` separately from `start` so continuing an existing local or remote branch preserves the ticket and commits. Apps offer Check out for local branches without a worktree as well. Refuse registered worktrees and occupied destinations to protect existing work.

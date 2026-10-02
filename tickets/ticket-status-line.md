---
id: ticket-status-line
type: feature
title: Show the current ticket in the Claude Code status line
status: wontfix
priority: low
size: S
created: 2026-10-02
updated: 2026-10-02
---

## Objective

Show the ticket a Claude Code session is working on in the status line: its id (the branch name), status and the next Plan item, or nothing when the session isn't on a ticket branch. Then the user can see where the session is, for example after it moved into a child ticket's worktree on its own. Found while working on resume-ticket-session, which makes continuing work possible with /safanoria resume; this makes it visible. Claude Code passes the session's working directory to the status line command on stdin; a 'safanoria resume --format json' run there (or a lighter 'safanoria here') could give the data.

## Acceptance Criteria

## Plan

## Work Log

- **2026-10-02** · status · Obsolete: it showed where a session had moved to. Sessions now live in their ticket's worktree and get the ticket from the SessionStart hook (`safanoria context`), so they don't move.

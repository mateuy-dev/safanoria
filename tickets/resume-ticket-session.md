---
id: resume-ticket-session
type: feature
title: Make it easy to continue work on a ticket, also after a restart
status: backlog
priority: high
size: S
created: 2026-10-02
updated: 2026-10-02
related: [worktree-no-prompt]
---

## Objective

When a ticket is started from a Claude Code session opened in the main checkout, the skill
switches the session into the ticket's worktree (`../safanoria--<id>`) with `EnterWorktree`.
Claude Code still saves the session under the folder where it started. After a restart:

- `claude --resume` run from the worktree probably doesn't list the session. You have to know
  to resume it from the main checkout.
- The resumed session may start in the main checkout on `main`, not in the ticket's worktree.
  If the agent then continues working, it edits `main`.
- Switching back into the worktree shows the permission prompt again (see
  `worktree-no-prompt`).

We want work on a ticket to continue in the right worktree and branch after a restart, without
the user having to remember where the session started.

The user may also not know which worktree the work is in. While working on a parent ticket, the
session can start a child ticket and create and switch into the child's worktree
(`../safanoria--<child-id>`) on its own. The user only knows "I was working on the parent". To
continue, they shouldn't need to know about the child, its branch or its worktree.

Think about different ways to make continuing work easy, for example:

- The user says "continue <ticket>" (or just "continue") in any session, in any checkout, and
  the skill finds where the work is: the ticket's `in-progress` children, their branches and
  worktrees, and the latest Work Log entries. Then it switches there, or says where to go.
- A CLI command (e.g. `safanoria status` or `safanoria resume [<id>]`) that lists the tickets in
  progress with their branch, worktree and last Work Log entry, and prints what to run to
  continue.
- The Work Log records where the work moved to (e.g. "started child `<id>`, worktree `…`"), so
  the parent ticket points to the child's worktree.
- Showing the current ticket and worktree in the Claude Code status line, so the user can see
  where the session is.

Ideas to look at (nothing decided yet):

- The skill checks where it is before working on an `in-progress` ticket: if the current
  branch isn't the ticket's id, switch into the worktree first, or stop and say so.
- Recommend starting ticket sessions in the worktree itself (`cd ../<project>--<id> && claude`),
  so the session is saved there.
- Find out whether worktrees under `.claude/worktrees/` (`worktree-no-prompt`) make Claude Code
  list and reopen the session in the worktree.
- Document how to resume (README and the skill).

## Acceptance Criteria

## Plan

## Work Log

- **2026-10-02** · status · Created from a user question: after a restart, should a ticket's
  session be resumed from the worktree or from main? Checked `~/.claude/projects/`: a session
  that moved into several ticket worktrees is saved only under the main checkout's folder, and
  the worktrees' folders have no sessions.
- **2026-10-02** · note · Scope widened at the user's request: look for different ways to make
  continuing work easy, also when the session switched into a child ticket's worktree without
  the user knowing.


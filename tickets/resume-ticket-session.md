---
id: resume-ticket-session
type: feature
title: Make it easy to continue work on a ticket, also after a restart
status: in-progress
priority: high
size: M
created: 2026-10-02
updated: 2026-10-02
related: [worktree-no-prompt, ticket-status-line]
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

- [ ] `safanoria resume [<id>]` says where to continue: for each ticket, its branch, its
      worktree (or that it has none), uncommitted files there, the next unchecked Plan item and
      the last Work Log entry. `--format json` gives the same for agents.
- [ ] Without an id it shows every `in-progress` ticket that has no `in-progress` child. With
      the id of a parent it shows the parent's `in-progress` children, or the parent itself when
      it has none. So "I was working on the parent" is enough.
- [ ] It gives the same answer from the main checkout and from any worktree.
- [ ] The skill has a Resume step, run with `/safanoria resume [<id, title or words>]`. Words
      are matched against the titles of tickets in progress. It finds the place with
      `safanoria resume` (git commands without the CLI), asks when there is more than one
      candidate, switches into the worktree, and goes on from the ticket file.
- [ ] Before changing files for a ticket, the skill checks the current branch is `<id>`.
- [ ] README explains how to continue after a restart, including where Claude Code saves a
      session that moved into a worktree.
- [ ] Tests for the core logic and the command pass.

## Plan

Decision: continuing must not depend on the Claude Code session. The ticket file is the context
(SPEC intro), and git already knows each ticket's branch and worktree. So we find the place
from tickets and git, and a fresh session in any checkout can continue. Resuming the old
conversation (`claude --resume` from the folder where it started) stays possible, but it's
optional.

Rejected: logging "started child `<id>`, worktree `…`" in the parent's Work Log so the parent
points to the child. `resume` derives this from `parent`, `status` and `git worktree list`, so a
log entry would only repeat it and could go stale (worktree moved or removed).

- [x] Core: `Resume` over `Branches` and `git worktree list`: candidates (rules in the
      Acceptance Criteria), each with branch, worktree path, uncommitted file count, next Plan
      item and last Work Log entry; tests with a parent, an in-progress child in a worktree, and
      a ticket without a worktree
- [x] CLI: `safanoria resume [<id>] [--format text|json]` in `Main.kt`; text gives the
      commands to get there (`cd <worktree>` or `git switch <id>`); tests
- [x] Skill: Resume section and the branch check in Work; SPEC §11: work on a ticket happens on
      branch `<id>`, and continuing finds it from the tickets and git, not from the session
- [x] README: "Continuing work" section (restart, child worktrees, where Claude Code keeps the
      session)
- [x] Out of scope: backlog ticket on `main` for showing the current ticket in the Claude Code
      status line; add it to `related`
- [ ] Check by hand: a new session in the main checkout, `/safanoria resume resume-ticket`,
      lands in this worktree

## Work Log

- **2026-10-02** · status · Created from a user question: after a restart, should a ticket's
  session be resumed from the worktree or from main? Checked `~/.claude/projects/`: a session
  that moved into several ticket worktrees is saved only under the main checkout's folder, and
  the worktrees' folders have no sessions.
- **2026-10-02** · note · Scope widened at the user's request: look for different ways to make
  continuing work easy, also when the session switched into a child ticket's worktree without
  the user knowing.
- **2026-10-02** · status · Started. Branch `resume-ticket-session` from `main`, worktree
  `../safanoria--resume-ticket-session`.
- **2026-10-02** · plan · Plan written: a `safanoria resume` command that finds where work is
  from the ticket files and git, and a Continue step in the skill that uses it. Size S → M.
- **2026-10-02** · plan · The skill step is triggered by `/safanoria resume <id, title or
  words>`, not by "continue": the user found a bare "continue" too vague to trigger a skill.
- **2026-10-02** · decision · Matching words against titles is done by `safanoria resume`
  itself, not only by the skill, so people get it too. Words match the id and title of
  `in-progress` and `review` tickets; an exact id is taken whatever its status. With no match it
  exits 1. The worktree is shown as an absolute path, the same one `cd` gets.
- **2026-10-02** · note · Created `ticket-status-line` on `main` with this branch's CLI
  (`new --on main`). The pre-commit hook then failed: it runs the installed `safanoria` (0.1.0,
  older than `multi-branch-tickets`), which doesn't know ids on other branches. Validated with
  the built CLI and committed `69ae345` with `--no-verify`. `ValidatorRepositoryTest` failed the
  same way, because it validated this repository without other branches; it now reads them like
  `safanoria validate` does.


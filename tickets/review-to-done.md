---
id: review-to-done
type: feature
title: "Review to done: land a ticket in one step, reopen it, and do both from the app"
status: done
priority: medium
size: L
created: 2026-10-05
updated: 2026-10-05
---

## Objective

Safanoria covers both ends of review → done but leaves everything in between to be done by hand. Today a ticket takes four steps after the work is complete, and Safanoria does two of them:

1. `safanoria-cli finish <id>` commits `<id>: review` on the branch.
2. By hand: go to the main checkout and `git merge <id>`.
3. `safanoria-cli finish <id> --done` adds a separate `<id>: done` commit on the target.
4. By hand: remove the worktree and delete the branch.

What goes wrong with that:

- `done` is recorded separately from the merge it describes. Between steps 2 and 3 the target says `review` for something already merged, and with a pull-request workflow nobody is at a terminal to run step 3.
- `review` checks nothing: `Finish.prepare` only looks at the status. Uncommitted work in the worktree, a branch behind its target, pending Learnings and unchecked Acceptance Criteria all go unnoticed.
- There is no way back. A review that finds problems means editing the status and the Work Log by hand, although SPEC §6.1 requires a logged reason.
- Nothing cleans up: merged branches pile up (this repository has twelve) and worktrees are removed by hand.
- The agent session can't finish its own ticket: the skill sets `done` only when the user says it is merged.
- The app stops at review: a ticket in review only offers "Open terminal".

Wanted: after the work is complete, the person runs `finish`, reviews, and then one action lands the ticket or sends it back.

**Land in one step.** A command (working name `safanoria-cli merge <id>`; `finish --merge` is the alternative) for a ticket in `review`, runnable from any checkout:

- Refuses on uncommitted work in the ticket's worktree, or if the result wouldn't validate.
- Merges branch `<id>` into its target (SPEC §14.1) with a merge commit, and sets `status: done`, the Work Log entry and the parent's Plan item inside that merge commit, so "done means merged" is true by construction and there is no separate `<id>: done` commit.
- Removes the worktree and deletes the branch.
- Doesn't push: everything it does stays local and undoable.
- On a merge conflict it stops and leaves things as they were, saying what to do.

**Merges made elsewhere** (GitHub pull requests). A `review` ticket on its target whose branch is merged is unambiguously done:

- `finish --done` without an id promotes all of them, and cleans up their worktrees and local branches.
- `release` does the same sweep before stamping, so a forgotten one isn't left out of a version.
- `validate` warns about a ticket that is merged but still `review`.

**`finish` checks before setting `review`.** It refuses on uncommitted changes in the worktree and on a branch that is behind its target; it lists pending Learnings and unchecked Acceptance Criteria without blocking (SPEC §7.6: tools do not block on them). Then it prints what there is to review (the diff stat against the target) and the command for the next step.

**Reopen.** `safanoria-cli reopen <id> --reason "…"` moves a `review` ticket back to `in-progress` and logs the reason, in the same worktree and branch.

**In the app.** A ticket in review gets two new buttons in the action bar, next to "Open terminal":

- **Merge and finish**: what the landing command does. Asks first, like Start and Finish.
- **Back to in progress**: the reopen, asking for the reason.

Both go through `core`, shared with the CLI, as Start and Finish do (`TicketStore`, `TicketAction`). The Finish button shows what `finish` refused or listed.

**Spec, skill, README.** SPEC §11.4 says tools commit only the ticket; landing merges code, so the lifecycle text has to allow it (and say `done` MAY be set in the merge itself). The skill's Finish section lets the session land or reopen the ticket when the user says so. The README's Finish section and command table follow.

Decided (see the Work Log): the commands are `merge` and `reopen`; `merge` takes only tickets in
`review`; the sweep keeps a worktree with uncommitted files, and its branch; one ticket, no children.

## Acceptance Criteria

- [x] One command takes a `review` ticket to `done`: merged into its target, `done` and the parent's Plan item in the merge commit, worktree removed, branch deleted, nothing pushed.
- [x] It refuses, changing nothing, on uncommitted work, a merge conflict, or a result that wouldn't validate.
- [x] `finish --done` without an id, and `release`, promote `review` tickets whose branch is already merged; `validate` warns about them.
- [x] `finish` refuses on uncommitted changes or a branch behind its target (commits that only touch tickets don't count), and lists pending Learnings and unchecked Acceptance Criteria.
- [x] `reopen <id> --reason` sets `in-progress` and logs the reason.
- [x] In the app, a ticket in review has "Merge and finish" and "Back to in progress", each asking first.
- [x] SPEC, skill and README describe the new flow.

## Work Log

- **2026-10-05** · status · started
- **2026-10-05** · decision · Commands: `merge <id>` and `reopen <id> --reason`. `finish --merge` was
  rejected: `finish` already means two things (`review`, and `--done` for merges made elsewhere).
- **2026-10-05** · decision · `merge` takes only `review`, not `in-progress`: `finish` is where the
  branch is checked (committed, not behind), and `review` is the person saying the work is complete.
- **2026-10-05** · decision · The merge is computed with `git merge-tree --write-tree` (git 2.38) and
  committed with plumbing, then the target moves by `merge --ff-only` where it is checked out, else
  by `update-ref`. No checkout is touched until the merge commit exists, so a conflict or an invalid
  ticket leaves nothing to undo, and it works from any checkout. `git merge --no-commit` in the
  target's worktree was rejected: it needs the target checked out and a `merge --abort` on failure.
- **2026-10-05** · decision · `finish` counts a branch as behind only for target commits that change
  something outside the ticket directory. Deviation from the Objective: tickets are created on
  `mainBranch` all the time (SPEC §14.2), so a plain "behind" would refuse almost every `finish`.
- **2026-10-05** · decision · The sweep sets `done` whatever the worktree has, since merged is a fact,
  but keeps a worktree with uncommitted files, and its branch, and says so. `release` promotes in
  the files it writes instead of committing, as it never commits.
- **2026-10-05** · decision · Merged elsewhere is "in `review` on its target", the condition
  `finish <id> --done` already had, not a branch lookup: it also covers a branch deleted after a
  squash merge. `validate` warns (`review-merged`) only where the target or a single commit is
  checked out, not on every branch that inherited the copy.
- **2026-10-05** · decision · Not split into children: the parts share `Finish`/`Land` in `core` and
  were done in one session.
- **2026-10-05** · status · review
- **2026-10-05** · status · done

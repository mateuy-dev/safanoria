---
id: review-to-done
type: feature
title: "Review to done: land a ticket in one step, reopen it, and do both from the app"
status: backlog
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

To decide in the ticket: the command names; whether the landing command also accepts a ticket still `in-progress` (review skipped, as §6.1 allows); what the sweep does with a worktree that has uncommitted files; and whether this is split into children (landing and sweep, `finish` checks, reopen, app buttons).

## Acceptance Criteria

- [ ] One command takes a `review` ticket to `done`: merged into its target, `done` and the parent's Plan item in the merge commit, worktree removed, branch deleted, nothing pushed.
- [ ] It refuses, changing nothing, on uncommitted work, a merge conflict, or a result that wouldn't validate.
- [ ] `finish --done` without an id, and `release`, promote `review` tickets whose branch is already merged; `validate` warns about them.
- [ ] `finish` refuses on uncommitted changes or a branch behind its target, and lists pending Learnings and unchecked Acceptance Criteria.
- [ ] `reopen <id> --reason` sets `in-progress` and logs the reason.
- [ ] In the app, a ticket in review has "Merge and finish" and "Back to in progress", each asking first.
- [ ] SPEC, skill and README describe the new flow.

## Work Log


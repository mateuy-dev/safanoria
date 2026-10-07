---
id: agent-sets-review
type: feature
title: The agent sets review itself when the implementation is finished
status: in-progress
priority: medium
size: S
created: 2026-10-07
updated: 2026-10-07
---

## Objective

A ticket is never in `review` while it is actually being reviewed. The agent finishes the implementation, stops, and leaves the ticket `in-progress`; `review` is set only when the person says "merge it", seconds before the merge. In `windows-gui-merge-test-fails` the code was committed at 11:02, and `<id>: review` and `<id>: merge (done)` are 4 seconds apart at 11:20; in `issue-branch-fails-validate` they are 20 seconds apart.

So `in-progress` means both "the agent is working" and "waiting on the person", and `review` tells nothing: across several worktrees, nobody can see which tickets wait for them.

The spec doesn't ask for this. §11.4 says "Set `status: review` when the work is complete", and unlike Start and Land it has no "only when the human says so". The skill narrows it: its Finish section starts with "When the user says the work is done".

Wanted:

**The agent finishes on its own.** When it considers the implementation complete, the session runs `safanoria-cli finish <id>` without being asked: everything is committed, the project's tests pass, the Acceptance Criteria it can check are checked, and it has no open question for the person. It then tells the person the ticket is in review and what `finish` listed as still open. If any of those doesn't hold (a question pending, a test failing) the ticket stays `in-progress` and the agent says why. Starting and landing stay the person's call; this is safe to do unasked because it is one ticket-file commit on the ticket's own branch, pushes nothing, and `reopen` undoes it.

**Small review fixes don't leave `review`.** The agent's "done" is often early, and §6.1 requires a Work Log entry with the reason for every move backward. With the agent finishing on its own, each "change this one thing" would be a `reopen` and another `finish`, and the Work Log would fill with them. So: changes the review asks for MAY be committed on the branch while the ticket stays in `review`; `reopen` is for a review that sends the ticket back to real work. Where that line is can't be checked by a tool: the skill should give the agent a rule of thumb (a fix made in the same exchange stays in review; new scope, or work that will take another session, reopens).

To decide while doing it:

- Whether `merge` should run the checks of `finish` again (branch behind its target, uncommitted work) now that commits can follow the `review` commit. It already refuses on uncommitted work.
- Whether the spec should say who sets `review` (an agent MAY, without being asked), so that other tools and skills following it do the same, or whether that belongs only in the skill.

## Acceptance Criteria

- [x] The skill tells the session to run `finish` on its own when the implementation is complete, with the conditions for it, and to report what is still open.
- [x] The skill says when a review change stays in `review` and when it needs `reopen`.
- [x] SPEC §6.1 and §11 allow commits on the branch of a ticket in `review`, and still require the logged reason when it goes back to `in-progress`.
- [x] The two open questions in the Objective are decided and logged.

## Work Log

- **2026-10-07** · status · started
- **2026-10-07** · decision · `merge` doesn't run `finish`'s checks again. Uncommitted work it already refuses. "Behind its target" would refuse most merges for nothing: with several tickets open the target moves after every review, and what matters then (a conflict, tickets that wouldn't validate) `merge` checks on the merged tree. Commits after `review` need no check a tool can make: a test covers that `merge` takes them.
- **2026-10-07** · decision · The spec says who sets `review` (§11.4: an agent MAY without being asked, SHOULD when the work is complete and no question is open), not only the skill. Start and Land carry "only when the human says so"; Finish said nothing, and the skill read that as "wait to be told". Other skills following the spec would do the same.
- **2026-10-07** · decision · The SessionStart text (`context`) also tells an in-progress session to finish on its own: the skill's Finish section is far from where the session reads at the moment it stops, the hook text is always in front of it.

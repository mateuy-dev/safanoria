---
id: review-mode
type: feature
title: "Review mode: safanoria-cli review <id> and review --end"
status: backlog
priority: medium
size: M
created: 2026-10-22
updated: 2026-10-22
---

## Objective

When Claude finishes a ticket (or a child), the human wants to check how it was programmed, usually in IntelliJ (Kotlin projects). Today seeing exactly a ticket's changes there is manual and error-prone: Git → Branches → "Compare with Current" works but is awkward, "Show Diff with Working Tree" mixes in everything new on the target, and the human has to know the ticket's target (main, or the parent's branch for a child).

The process worked out (in a conversation, not yet tooling) is a **review mode** in the ticket's worktree:

- **Start**: `git checkout --detach` then `git reset --soft $(git merge-base <target> <id>)`. The branch `<id>` doesn't move; the whole ticket shows as uncommitted (staged) changes, so IntelliJ's Commit window lists every changed file, the gutter marks every changed line, and navigation, inspections and tests work on the real code.
- **End**: `git reset --soft <id> && git switch <id>`. HEAD returns to the ticket tip and the worktree is back on the branch; any edits the human made during the review stay as the only uncommitted changes, ready to commit on the branch. Never `reset --hard`, which loses them. Even if the human committed in review mode by mistake, this end still leaves exactly their edits.

Wanted: `safanoria-cli review <id>` and `safanoria-cli review --end`, so neither the human nor an agent session has to remember the git commands or the target, and a fresh or summarized session can tell the worktree is in review mode.

`review <id>`:
- Resolves the target per SPEC §14.1 (parent's branch with `childrenMergeInto: parent`, else mainBranch) and works in the ticket's worktree.
- Refuses on uncommitted changes (they'd be mixed into the review), and on a worktree already in review mode.
- Warns where the merge-base diff is misleading: the branch contains merges whose other side isn't in the target (e.g. a child merged main but its parent didn't: those changes show as the ticket's); the ticket is already merged into its target (empty diff: review the merge commit instead).
- Optionally (`--since <commit>`) reviews only what changed after a commit, e.g. the fixes made after a first review.

`review --end`:
- Leaves review mode as above, keeping the human's edits; says which files they changed.
- Works from the state only (detached HEAD in a ticket worktree), so it doesn't depend on the session remembering it.

Also:
- The review-mode state must be detectable (by `review --end`, `merge`, `finish`, the status line/board), e.g. a marker or detached HEAD + worktree of branch `<id>`. `merge` already refuses on uncommitted work; it should say "in review mode: run review --end" instead of a generic refusal.
- The `safanoria` skill: "I want to review" → `review <id>`; "finish review" / "look at my REVIEW comments" / "reviewed, merge" → `review --end` first, then commit the human's edits (Work Log `review` entry if a decision), fix, or `merge`. The agent must never edit or commit while in review mode. Commenting `// REVIEW: …` in code is a supported way to give feedback.
- Document it in README (the IntelliJ flow).

## Acceptance Criteria

## Work Log


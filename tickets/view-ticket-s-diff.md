---
id: view-ticket-s-diff
type: feature
title: "Quick look at a ticket's diff"
status: in-progress
priority: medium
size: S
created: 2026-10-23
updated: 2026-10-23
---

## Objective

Let the user take a quick look at a ticket's changes in one step, without review mode.

Review mode (ticket review-mode, `safanoria-cli review`) is for a thorough review: it stages the ticket's whole diff in its worktree for IntelliJ, but the user has to open (or switch to) the worktree's project there, which is slow. Often they only want to see what changed. The alternatives inside IntelliJ alone (Git → Branches → Compare with Current, Show Diff with Working Tree) are awkward, mix in what is new on the target, and require knowing the ticket's target (main, or the parent's branch for a child).

Wanted: `safanoria-cli diff <id>`, the ticket's changes from its merge-base with its target (`git diff <target>...<id>`), in the terminal or (`--tool`) in git's diff tool via `git difftool --dir-diff`, which copies only the changed files into two temporary directories, so no project is loaded (IntelliJ's `idea diff` opens just a diff window; Meld and the like work too). The same as a Diff action in the GUI and TUI ticket actions. It changes nothing in the worktree.

Considered and left out: opening the worktree's project in IntelliJ after `review` (the slow loop this avoids); a diff viewer inside the GUI/TUI (a later ticket if switching apps still feels slow); GitHub's compare page (needs a pushed branch).

## Acceptance Criteria

- [x] `safanoria-cli diff [<id>]` shows the ticket's changes against its target from their merge-base; `--stat` only the files; `--tool` in git's diff tool; `--uncommitted` up to the worktree's files
- [x] It changes nothing, and works in review mode and on a ticket started on another computer (`origin/<id>`); it refuses a ticket not started or already merged, and warns as `review` does about merges from outside the target
- [x] The GUI and TUI have a Diff action on started tickets that opens git's diff tool
- [x] README documents it, with how to set IntelliJ (or Meld) as git's diff tool; the skill knows it

## Work Log

- **2026-10-23** · status · started
- **2026-10-23** · decision · A quick look (`diff`) instead of review mode plus opening the worktree's project in IntelliJ: the user finds that loop slow and keeps `review` for thorough reviews. Committed changes by default; `--uncommitted` adds the worktree's.
- **2026-10-23** · decision · The diff tool is git's own (`diff.tool`, `git difftool --dir-diff`): no IntelliJ launcher of our own nor `$SAFANORIA_DIFFTOOL`. IntelliJ is one `difftool.idea.cmd` (README). The GUI refuses without `diff.tool`, since git would guess, maybe a terminal tool nobody would see; the TUI runs it on its terminal until it closes, so any works there.

---
id: view-ticket-s-diff
type: feature
title: "View a ticket's diff in IntelliJ"
status: in-progress
priority: medium
size: S
created: 2026-10-23
updated: 2026-10-23
---

## Objective

Let the user open a ticket's changes in IntelliJ in one step, instead of the manual steps they need today.

Review mode (ticket review-mode, `safanoria-cli review`) already makes the ticket's whole diff show as staged changes in its worktree, so IntelliJ's Commit window, gutter, navigation and tests work on it. But the user still has to run `review` in a terminal, then open (or switch to) the worktree's project in IntelliJ and find the changes. The alternatives inside IntelliJ alone (Git → Branches → Compare with Current, Show Diff with Working Tree) are awkward or mix in what is new on the target, and require knowing the ticket's target (main, or the parent's branch for a child).

Wanted: an action that puts the ticket in review mode (or reuses it if already there) and opens its worktree in IntelliJ showing the changes, available where the user acts on tickets (CLI, and the GUI/TUI ticket actions next to Finish). To settle while working it: how IntelliJ is launched (`idea` launcher / JetBrains Toolbox script, configurable command in safanoria.yaml or a user setting, Linux/macOS/Windows), whether it opens the project only or also a diff view (`idea diff` of merge-base vs branch as a lighter alternative that doesn't touch the worktree), and what happens when the ticket has no worktree or has uncommitted changes (the same refusals as `review`).

## Acceptance Criteria

## Work Log

- **2026-10-23** · status · started

---
id: parent-partial-release
type: feature
title: Partial release of a parent
status: backlog
priority: medium
size: M
created: 2026-11-04
updated: 2026-11-04
---

## Objective

Let a parent with `childrenMergeInto: parent` ship the children already merged into its branch, without finishing the parent and without changing how the remaining work proceeds.

Today (spec 1) the only way to release finished children early is to switch the parent to `childrenMergeInto: main`, which gives up shipping the set together for good. What is wanted instead: merge the parent branch into `mainBranch` mid-way, stamp the release so the `done` children get `resolvedIn`, and then carry on exactly as before: remaining children branch from and merge into the parent branch, the parent's own steps continue there, and the parent merges into `mainBranch` when it is `done`.

This already works by hand in spec 1, but the spec doesn't describe it and two things go wrong (§14.1):
1. Right after the merge, branch `<parent>` counts as merged into `mainBranch`, so the parent's branch resolves to `mainBranch`: in-progress children's target becomes `mainBranch` and tools read the parent from `mainBranch`, until the parent branch has a commit `mainBranch` lacks.
2. Stamps land only on `mainBranch`, while children merged into the parent are read from the parent branch's copy, which has no `resolvedIn`, so they look unreleased until `mainBranch` is merged back.

Manual workaround found: on the parent branch log the partial release in the Work Log and commit; plain `git merge` of the parent into `mainBranch` (not a land/merge tool, which would set `done`); release and stamp on `mainBranch`; then `git merge --no-ff mainBranch` back into the parent branch, which brings the stamps and makes the branch unmerged again.

To do: describe this flow in the spec (§8.1 / §9 / §11 / §14.1), decide whether §14.1 needs a rule so a parent that is `in-progress` keeps its branch as target even when momentarily merged, and add CLI support (e.g. a command that does the merge, optional stamping, and the merge-back in one step, refusing on uncommitted work or conflicts and pushing nothing). Caveat to state: everything on the parent branch ships, including the parent's own partial work, so it must be releasable.

## Acceptance Criteria

## Work Log


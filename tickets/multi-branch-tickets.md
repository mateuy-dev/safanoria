---
id: multi-branch-tickets
type: feature
title: Read and create tickets across git branches
status: backlog
priority: high
size: M
created: 2026-10-02
updated: 2026-10-02
related: [v1-tooling]
---

## Objective

A ticket's file changes across branches, but the spec and the planned tools read only the current
checkout: validation (§12), stamping (§9) and the `board` command planned in `v1-tooling`.
That gives a wrong picture:

- After Start, the ticket's real state (status, Acceptance Criteria, Plan, Work Log) is on branch
  `<id>`. `main` still has the `backlog` copy until the merge, so its status is out of date.
- Children are created while planning the parent, which happens on the parent's branch, so they
  exist only there until the parent merges.
- Out-of-scope tickets found mid-work (§11.4) are created on the current branch. They are
  invisible elsewhere, and they are lost if that branch is abandoned.
- Id uniqueness (§3) can't be checked: two branches can create the same id without noticing.
  §11 Create checks only that no branch has the name, not that no branch has the ticket file.

We want one rule, shared by every tool (CLI, viewers, apps), that says which copy of a ticket is
the real one across branches. We also want ticket creation rules that keep the real copies
discoverable.

## Acceptance Criteria

- [ ] SPEC.md defines which copy of a ticket is the real one when it exists on several branches
      (or worktrees), and every tool that lists or validates tickets follows it
- [ ] SPEC.md says on which branch a new ticket is created: top-level and out-of-scope tickets
      on `mainBranch`; children on their parent's branch; a fallback for when `mainBranch`
      can't be written to
- [ ] Id uniqueness covers ticket files on every branch, not just branch names
- [ ] The CLI board shows each ticket's real state, read from all local branches (and
      `origin/*` with a flag) without checking any branch out
- [ ] Uncommitted edits in a checked-out worktree show up in the board
- [ ] Tickets that exist only on a non-main branch are listed and marked with that branch
- [ ] `validate` resolves ids across branches (no false "unknown id" for a `related` ticket that
      is only on `main`) and reports the same id created separately on two branches
- [ ] The CLI can create a ticket on `mainBranch` while the user works on another branch,
      without touching their working tree
- [ ] The skill follows the new creation rules

Out of scope:
- Syncing or fetching remotes automatically (the user runs `git fetch`)
- Moving tickets out of the code repository (separate ticket branch or repo)

## Plan

- [ ] SPEC: new section "Tickets across branches" with the resolution rule (see Design).
- [ ] SPEC §3 / §11 Create: uniqueness across all branches; creation branch rules and fallback.
- [ ] SPEC §12: cross-branch checks (duplicate id on different branches; branch `<id>` gone
      while `main` says `in-progress`).
- [ ] Skill: creation rules (out-of-scope → `mainBranch`; children → parent's branch); don't
      edit an out-of-scope ticket from the branch that found it.
- [ ] CLI (needs the CLI from `v1-tooling`): ticket source that reads `<ref>:<dir>/` with
      `git ls-tree` / `git show` (or `cat-file --batch`), plus worktree files for
      checked-out branches.
- [ ] CLI: resolution rule; `board` and `validate` use it; `--remote` includes `origin/*`.
- [ ] CLI `new --on <branch>`: commit on another branch through plumbing (temporary index,
      `commit-tree`, `update-ref`), or in the worktree where that branch is checked out.
- [ ] Tests with a fixture repository: unstarted, started, merged-and-kept branch, child
      merged into the parent's branch, out-of-scope ticket on main, duplicate id.

## Design

### Which copy is the real one

Decided by git state, not by `status`. The `main` copy of a started ticket still says `backlog`.

For ticket `<id>`, its **target** is the parent's branch if it has a parent with
`childrenMergeInto: parent`, otherwise `mainBranch`.

1. If branch `<id>` exists and is not merged into its target
   (`git merge-base --is-ancestor <id> <target>` fails), the copy on branch `<id>` is the real
   one. If that branch is checked out in a worktree, use the working-tree file.
2. Otherwise, the real copy is the target's copy, resolved the same way. A child merged into an
   unmerged parent branch is therefore read from the parent's branch.
3. A ticket found only on some other branch is listed with a marker such as
   `(only in herd-locations)`.

Rule 1's merge check also covers branches kept after merging: `main` wins, so a `resolvedIn`
added by stamping isn't hidden by the old branch copy.

### Where tickets are created

- Top-level tickets, including out-of-scope ones found mid-work: on `mainBranch`.
  - The id is reserved immediately.
  - The ticket survives if the branch that found it is abandoned.
  - It can be prioritized on its own.
- Children: on the parent's branch, when `childrenMergeInto: parent`. The child's branch is
  created from the parent's branch, so the file must be there. If it lived only on `main`, the
  parent branch would have to merge `main` first or hit an add/add conflict.
- Fallback: if `mainBranch` can't be written to (protected, PR-only), the ticket MAY be created
  on the current branch. Cross-branch reading still finds it.

Rejected: a MUST to always create on `mainBranch`. It breaks for protected branches and for
children. A dedicated ticket branch or repo is also rejected: it breaks "commit code and ticket
together" (§11.4).

### Known consequences

- An out-of-scope ticket created on `main` must not be edited from the branch that found it.
  Editing it there risks an add/add conflict at merge.
- A ticket on the branch can name an id in `related` that only exists on `main`. Validation
  must resolve ids across branches.

## Work Log

- **2026-10-02** · status · Created from the design discussion on how the CLI sees tickets on several branches.

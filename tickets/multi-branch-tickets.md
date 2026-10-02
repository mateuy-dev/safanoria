---
id: multi-branch-tickets
type: feature
title: Read and create tickets across git branches
status: in-progress
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
- [ ] Id uniqueness covers ticket files on every branch, not just branch names: `new` refuses an
      id whose file exists on any local or remote-tracking branch
- [ ] `board` and `list` show each ticket's real state, read from all local branches (and
      `origin/*` with `--remote`) without checking any branch out; `--checkout` keeps today's
      behaviour (working tree only)
- [ ] Uncommitted edits in a checked-out worktree show up in `board` and `list`
- [ ] Tickets that exist only on a non-target branch are listed and marked with that branch
- [ ] `validate` still checks the files of the current checkout, but resolves referenced ids
      across local and remote-tracking branches (no false `ref-unknown` for a ticket that is
      only on `main`), and warns when the same id was created separately on two branches
- [ ] `new --on <branch>` creates (and commits) a ticket on another branch without touching the
      current working tree
- [ ] Outside a git repository, or with a single branch, every command behaves as today
- [ ] The skill follows the new creation rules
- [ ] Git cost stays bounded: one process per branch plus one per distinct ticket version, not
      one per ticket per branch

Out of scope:
- Syncing or fetching remotes automatically (the user runs `git fetch`)
- Moving tickets out of the code repository (separate ticket branch or repo)
- `release`: it keeps stamping the `mainBranch` checkout it runs on (§9)

## Plan

- [x] SPEC: new section "Tickets across branches" (resolution rule, see Design); §3 uniqueness
      across branches; §11 Create: on which branch, with the fallback; §12 cross-branch checks.
- [x] Skill: creation rules (`new --on <mainBranch>` for out-of-scope tickets, children on the
      parent's branch); don't edit an out-of-scope ticket from the branch that found it.
- [ ] core `Git`: branches (local, remote-tracking), worktrees (`worktree list --porcelain`),
      merged-into checks, `ls-tree` of the ticket dir, blob reads, merge-base.
- [ ] core `GitTreeFileSystem`: a read-only Okio `FileSystem` over one commit's tree, blobs read
      once and cached across branches, so `Repository` (config, tickets, templates, validator,
      `NewTicket`) works on any branch unchanged.
- [ ] core `Branches`: every branch's tickets (working-tree files for checked-out branches), the
      resolution rule, and where each real copy came from (for the "only on" marker).
- [ ] CLI `board` and `list` use it, with `--remote` and `--checkout`; both show the marker.
- [ ] CLI `validate`: ids from every branch for references; `id-created-twice` warning.
- [ ] CLI `new --on <branch>`: prepare against that branch's tree and commit there (see
      Implementation); `new` checks ids against every branch.
- [ ] Tests with a fixture repository (real `git init` in a temp dir): unstarted, started,
      started and checked out with uncommitted edits, merged-and-kept branch, child merged into
      the parent's branch, ticket only on a feature branch, created twice, `--on` both ways.
- [ ] README: board/list/new options, and what "real copy" means for the user.

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

### Implementation

- **Reading a branch.** For each branch: one `git ls-tree` of `<branch>:<dir>/` (blob ids).
  Read each distinct blob once with `git cat-file -p`; most tickets are identical across
  branches, so this is about one read per ticket plus one per change. `runCommand` has no stdin,
  so `cat-file --batch` is out. For a branch checked out in a worktree, read that worktree's
  files instead, which picks up uncommitted edits. The current checkout counts as such a worktree.
- **One code path.** A read-only `GitTreeFileSystem` lets `Repository(root, fs)` open any branch.
  Config, the ticket list, templates, the validator and `NewTicket.prepare` then need no changes.
- **Resolution** is as in "Which copy is the real one". The parent (for the target) is read from
  the `<id>` branch copy, else the `mainBranch` copy, else any copy. Merged checks use one
  `git branch --merged <target>` per target, not one call per ticket. With `--remote`, the local
  branch is used when it exists, else `origin/<name>`.
- **Validate stays on the checkout.** It is a pre-commit and CI check of the files being
  committed, so it keeps validating the working tree. Other branches only add known ids for
  `ref-unknown`. Remote-tracking branches are included here, because CI checkouts with
  `fetch-depth: 0` only have `origin/*`.
- **Created twice** = the same `<dir>/<id>.md` on two branches, absent at their merge-base.
  A started ticket is on both `main` and `<id>` but is present at the merge-base, so it isn't
  reported. It is a warning, not an error: the commit being validated can't fix it, and a stale
  remote branch must not block everyone. `new` refuses such ids, which is where it matters.
- **Rejected:** a §12 check for "branch `<id>` gone while `main` says `in-progress`". The result
  depends on which branches a clone has (CI, fresh clones), and when a branch is deleted
  unmerged, `main` still says `backlog` anyway.
- **`new --on <branch>`.**
  - If that branch is checked out in a worktree (usually `main` in the main checkout), write the
    files there and run `git -C <wt> commit -- <files>`. This commits only those paths and leaves
    the user's other staged work alone.
  - Otherwise, commit through plumbing. `update-ref` on a branch checked out elsewhere would
    desync that worktree, which is why the worktree case is separate.
    - `hash-object -w` the files.
    - Rebuild the trees bottom-up: `ls-tree` and `mktree < tmpfile`. A `<` redirect works in
      both `sh` and `cmd`; `GIT_INDEX_FILE=` doesn't.
    - `commit-tree -p <branch>`, then `update-ref <branch> <new> <old>`, so a concurrent change
      fails instead of being lost.
  - The commit message is `<id>: create`.
  - Without `--on`, `new` writes to the working tree as today. On a non-`mainBranch` branch
    without `--parent`, it prints a hint to use `--on <mainBranch>`.

## Work Log

- **2026-10-02** · status · Created from the design discussion on how the CLI sees tickets on several branches.
- **2026-10-02** · status · started
- **2026-10-02** · plan · Plan rewritten against the shipped CLI (`v1-tooling` is done); decisions in Design → Implementation.
- **2026-10-02** · decision · The new section is §14, not inserted before §11: §11–§13 are cited
  in code, docs and copies installed in other projects. The spec version stays 1: §14 only adds
  rules, and validation gets more lenient (fewer `ref-unknown`) plus one warning.

---
id: issue-branch-fails-validate
type: bug
title: Issue branch fails validate in CI with ref-unknown for a ticket created on main from it
status: in-progress
priority: medium
size: S
created: 2026-10-06
updated: 2026-10-06
---

## Objective

When a session on a ticket's branch finds out-of-scope work, it creates a new ticket on `mainBranch` (`safanoria-cli new --on main`, SPEC §14.2) and adds it to the current ticket's `related`. When that branch is then pushed, the CI run of `safanoria-cli validate` fails: the branch's ticket references an id whose file is not on the branch.

Seen in another project using Safanoria:

```
Run safanoria-cli validate
  1 problem in 1 file (9 tickets checked)
  tickets/cyl.md:12:72: error[ref-unknown]: related: no ticket 'device-credentials'
```

Locally this passes, because `validate` counts ids on other branches (local and remote-tracking) as known. In CI they are not there. Two likely causes, neither confirmed yet:

- The workflow uses `actions/checkout` with its default shallow, single-branch fetch, so `origin/main` doesn't exist in the runner. The README says `fetch-depth: 0` is needed, but this repository's own workflows (`tickets.yml`, `cli.yml`, `release.yml`) don't set it either, and nothing sets it up or checks it for a project.
- `--on main` commits to the local `main` only. If the ticket branch is pushed and `main` isn't, the new ticket exists on no remote branch, and no fetch depth helps.

We want the flow the skill and SPEC prescribe (new ticket on `mainBranch`, referenced from the branch that found it) to pass CI without the user having to know about either trap.

### Steps to reproduce

1. In a project whose CI runs `safanoria-cli validate` on push, start a ticket and work on its
   branch.
2. From that branch, create an out-of-scope ticket on `mainBranch`:
   `safanoria-cli new "…" --on main`.
3. Add the new id to the current ticket's `related` and commit.
4. Push the ticket's branch.

### Expected

CI validates the branch cleanly, as `validate` does locally: a `related` to a ticket that so far
exists only on `mainBranch` is not an error.

### Actual

CI fails:

```
tickets/cyl.md:12:72: error[ref-unknown]: related: no ticket 'device-credentials'
```

## Acceptance Criteria

## Work Log

- **2026-10-06** · status · started
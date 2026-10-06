---
id: issue-branch-fails-validate
type: bug
title: Issue branch fails validate in CI with ref-unknown for a ticket created on main from it
status: in-progress
priority: medium
size: S
created: 2026-10-06
updated: 2026-10-06
related: [new-crashes-ticket-directory]
---

## Objective

When a session on a ticket's branch finds out-of-scope work, it creates a new ticket on `mainBranch` (`safanoria-cli new --on main`, SPEC §14.2) and adds it to the current ticket's `related`. When that branch is then pushed, the CI run of `safanoria-cli validate` fails: the branch's ticket references an id whose file is not on the branch.

Seen in another project using Safanoria:

```
Run safanoria-cli validate
  1 problem in 1 file (9 tickets checked)
  tickets/cyl.md:12:72: error[ref-unknown]: related: no ticket 'device-credentials'
```

Locally this passes, because `validate` counts ids on other branches (local and remote-tracking) as known. In CI they are not there. Two causes, both reproduced in a scratch repository (either one alone gives the error):

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

- [x] The Safanoria Action validates a ticket branch that references a ticket existing only on
      `mainBranch`, after a default `actions/checkout`, without `fetch-depth: 0`.
- [x] Branch tips fetched without history (a shallow CI clone) don't produce false
      `id-created-twice` warnings.
- [x] Without the other branches, the `ref-unknown` output says why and how to fetch them.
- [x] Locally, `validate` warns (`ref-unpushed`) while a reference names a ticket that is only on
      a local branch no remote has; the warning goes once that branch is pushed.
- [x] This repository's workflows validate with every branch fetched.
- [x] README and skill describe both traps.
- [x] Seen passing on GitHub: the `release` workflow's Action test on this branch, which
      references a ticket that is only on `main`.

## Work Log

- **2026-10-06** · status · started
- **2026-10-06** · decision · The Action fetches the tip of every branch itself (`--depth=1` when the checkout is one commit) instead of asking for `fetch-depth: 0`: validate only needs the ids, and nobody has to know. A deeper clone is fetched without `--depth`, which would cut its history.
- **2026-10-06** · decision · In a shallow clone, copies with no merge-base aren't reported as `id-created-twice`: tips without history can't tell a started ticket (main's copy and its branch's) from an id created twice, and the first try at fetching tips warned on every started ticket.
- **2026-10-06** · decision · An unpushed `mainBranch` can't be fixed from CI, and `new --on` pushing would break "the CLI pushes nothing". So `validate` warns locally (`ref-unpushed`), where the pre-commit hook and the agent see it when the `related` is committed. Skipped when the clone has no remote-tracking branches (no remote to compare with).
- **2026-10-06** · note · Found while reproducing: `new` crashes when the ticket directory doesn't exist → `new-crashes-ticket-directory`.

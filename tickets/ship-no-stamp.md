---
id: ship-no-stamp
type: feature
title: "Ship without stamping; release stamps shipped children"
status: in-progress
priority: medium
size: S
created: 2026-11-04
updated: 2026-11-04
---

## Objective

Remove stamping from the partial release of a parent (`ship`, SPEC §8.1): the CLI (`--release`), the TUI and desktop app ship dialog (ShipForm's stamp/components/version fields), core `Ship.plan`/`Ship.versions`, and the spec text. Shipped children reach `mainBranch` `done` without `resolvedIn`, so the normal `safanoria-cli release` stamps them with the rest of that release (§9), like any other ticket.

Why: the ship dialog pre-filled the version from the component's version source, which in projects that bump the version at release time is the version already released. Stamping accepts an equal version, so children were silently stamped into a version that doesn't contain them. Stamping at release time is also how every other ticket works; one stamping step is simpler.

Side effect to fix: after a release, the stamps exist only on `mainBranch`, but shipped children are read from the parent branch's copy (their target, unmerged again after ship's merge-back, §14.1), so tools show them unreleased until `mainBranch` is merged into the parent branch. Decided fix: `release`, after stamping, merges `mainBranch` back (`--no-ff`) into the local branch of every `in-progress` parent with `childrenMergeInto: parent` whose branch contains stamped tickets (i.e. that shipped). Same guarantees as ship's merge-back: local branches only, pushes nothing, skips (with a warning, not a failure of the release) a branch with uncommitted changes in its worktree or a conflicting merge. Rejected alternative: changing §14.1 to read `resolvedIn` from `mainBranch` for tickets `done` there — a bigger spec change to which copy is real.

Update SPEC (§8.1 partial release, §9 stamping), the skill and README where they mention ship stamping, and the tests.

## Acceptance Criteria

## Work Log

- **2026-11-04** · status · started

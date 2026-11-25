---
id: ready-refinement
type: feature
title: "Ready status: refine tickets before starting"
status: review
priority: medium
size: M
created: 2026-11-25
updated: 2026-11-25
---

## Objective

Give `ready` a real meaning by adding a Refine step between Create and Start.

Today `ready` is never used: SPEC §7.1 requires the same content for `ready` as for `backlog` (a non-empty Objective), §11 has no step that moves a ticket to it, and the skill never mentions it. Meanwhile backlogs grow with short ideas that nobody has defined yet.

Intended use: when I see something to fix or have an idea, I create a `backlog` ticket that is just that, a short idea (can be one line). Later, the Refine step is where I or an agent define exactly what we need. `backlog` stays cheap so ideas are always written down; definition effort goes only to tickets close to being done, and the `ready` list becomes the short "next up" list.

Decisions from the discussion:
- **Refine** (backlog → ready) runs on `mainBranch` and commits only ticket files, like Create. No branch or worktree: a branch still means `in-progress`, and work still starts only when the human says so. It may read code and investigate, write or rewrite Objective, Acceptance Criteria, Design and Plan, split into children, add Learnings and triage (wontfix, merge duplicates). It must not change code.
- An agent refines only when the human asks; it MAY then set `ready` itself (like Finish → `review`). Refinement decisions go into the Work Log.
- `ready` MUST have Acceptance Criteria (in addition to Objective). It is a promise about what `ready` means, not a gate: `backlog` → `in-progress` stays allowed, refinement then happens as part of the work, as today.
- **Staleness**: plans written long before work starts go stale (code moves, assumptions break, scope shifts) and an old precise plan misleads more than a vague one. So Refine records `refinedAt`: the `mainBranch` commit (HEAD when refining, i.e. the code that was read) in frontmatter. Chosen over deriving it from git history (fragile with renames, later edits, re-refining) and over a Work Log line (not machine-readable). Re-refining overwrites it and adds a Work Log entry. Relies on `mainBranch` never being rewritten.
- **Start from `ready`**: the session first reviews `git log <refinedAt>..<mainBranch>` (and the diff as needed) for changes touching what the Plan/Design assume, updates them, and logs deviations. Refinement should favour slow-ageing content (Objective, Acceptance Criteria, key design decisions) over detailed implementation steps.
- Tools MAY show how far behind `refinedAt` is (e.g. "ready, 37 commits behind").

## Acceptance Criteria

- [x] SPEC defines the Refine step in §11 (on `mainBranch`, ticket files only, no code changes, only when the human asks) and updates §6.1/§7.1: `ready` requires Acceptance Criteria; §7.1's "no planning or approval step" wording is adjusted so refinement is optional, not a gate.
- [x] SPEC defines the `refinedAt` frontmatter field (a `mainBranch` commit) and Start's check of changes since it, with deviations logged.
- [x] JSON schema and `safanoria-cli validate` accept `refinedAt` and enforce Acceptance Criteria for `ready`.
- [x] The skill has a Refine flow and the Start-from-`ready` staleness check; README documents the backlog → ready → in-progress use.

## After Release

<!-- Add tasks as they become known. For tickets with requests, consider “reply to the requesters”. -->

## Work Log

- **2026-11-25** · status · started
- **2026-11-25** · decision · Refine records a full Git commit ID. Older ready tickets may lack it, so their starting session checks the current code directly.
- **2026-11-25** · status · review

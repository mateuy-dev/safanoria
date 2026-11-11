---
id: child-ticket-default
type: feature
title: "Tickets created in a ticket's session default to children"
status: done
priority: medium
size: S
created: 2026-11-09
updated: 2026-11-11
requests:
  - user: null
    who: Project maintainer
    channel: other
    date: 2026-11-09
resolvedIn:
  safanoria: 0.11.0
---

## Objective

When a user asks Claude or Codex to create a ticket from a session on a ticket's branch (a parent, or one of its children), the agent today often creates a top-level ticket on `mainBranch` (`--on main`). The skill pushes it there: Create step 3 says a top-level ticket goes on `mainBranch`, "From a command" (`/sc`, `/scsh`, `/scshc`, `/scshi`) says "It is a top-level ticket on `mainBranch`", and "Working on a ticket's branch" sends out-of-scope work to `mainBranch`. The user doesn't want that default.

Wanted: a ticket created in a session on a ticket's branch is, by default, a child of that ticket (`--parent <current id>`, created on the current branch, without `--on`), which adds it to the parent's Plan. When the current ticket is itself a child, the default parent is that same branch's ticket (the user's words: "A ticket created in a parent or a children should create the ticket as a child parent by default"; whether a child's session should instead attach to the child or to its parent is to be decided and logged, the child of the current ticket being the plain reading). Only when the user says so explicitly ("in main", "top-level", "separate ticket", out of this ticket's scope) is it created top-level on `mainBranch` with `--on`. On `mainBranch` itself nothing changes: tickets are top-level.

Update everywhere the default is stated, so Claude and Codex behave the same: `skill/SKILL.md` (Create step 3, From a command, Working on a ticket's branch / out-of-scope work), the command files in `skill/commands/` (sc, scsh, scshc, scshi and any other that creates), README.md, SPEC.md §14.2 and the §11 lifecycle text if they state the rule as a requirement, and the CLI note in `cli/.../New.kt` (`note: this is branch ...; a top-level ticket belongs on main`), whose interactive prompt should then default to the current ticket as parent. Keep the installed copies (`.claude/skills`, `.agents/skills`) in sync the way the project already does.

## User Requests

> in the current safanoria when a user creates a ticket using claude/codex inside a parent ticket many times a ticket in master is created. This should not be the default. A ticket created in a parent or a children should create the ticket as a child parent by dafault. Only in master if the user indicates it.
— Project maintainer · other · 2026-11-09

## Acceptance Criteria

- [x] Skills, creation commands, README and spec consistently default ticket sessions to children, with explicit top-level requests honored.
- [x] Interactive creation defaults to the session parent and updates its Plan; flags retain their explicit behavior.
- [x] Child sessions create siblings within the existing one-level hierarchy; research follow-up stays top-level and related.

## Work Log

- **2026-11-09** · status · started

- **2026-11-09** · decision · A child's session defaults to its existing parent (a sibling), preserving §8.1's one-level hierarchy rather than expanding nesting and lifecycle semantics. Research follow-up remains top-level and related because research cannot have children.
- **2026-11-09** · decision · Infer the parent only in interactive creation; scripts and agents keep explicit flags, with the CLI hint recommending the session parent and the skill supplying it.
- **2026-11-09** · status · review
- **2026-11-09** · status · done
- **2026-11-11** · release · safanoria 0.11.0

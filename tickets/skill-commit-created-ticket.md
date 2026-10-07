---
id: skill-commit-created-ticket
type: feature
title: "Skill: commit a created ticket automatically when no questions were asked"
status: in-progress
priority: medium
size: XS
created: 2026-10-07
updated: 2026-10-07
---

## Objective

When Claude creates a ticket through the safanoria skill and did not need to ask the user anything to write it, it should commit the new ticket file itself, instead of leaving it uncommitted in the working tree for the user to commit.

Today only `safanoria-cli new --on <branch>` commits; a ticket created on the current checkout (a top-level ticket while on `mainBranch`, or a child on its parent's branch) is left as an untracked file, and the skill's Create steps say nothing about committing it. The user then has to commit each ticket by hand (the `<id>: create` commits in the history).

If Claude had to ask the user something while creating the ticket, the content is still under discussion, so it is not committed automatically.

The change is expected in the skill's Create section (`skill/SKILL.md`), covering the `requests` entry and quote added after `new` so they land in the same commit.

## Acceptance Criteria

## Work Log

- **2026-10-07** · status · started

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

- [x] The skill's Create steps tell Claude to commit a ticket created on the current checkout, as `<id>: create`, when it asked the user nothing.
- [x] The commit holds the ticket with its `requests` entry and quote, the parent's Plan for a child, and no other uncommitted work.
- [x] A ticket Claude had to ask about is left uncommitted, and the user is told.

## Work Log

- **2026-10-07** · status · started
- **2026-10-07** · decision · Skill text only, as a last Create step after `validate`: the `requests` entry and quote are written after `new`, so a commit made by the CLI would miss them. The commit names its files (`git commit -- <files>`) so other uncommitted work on the checkout stays out.

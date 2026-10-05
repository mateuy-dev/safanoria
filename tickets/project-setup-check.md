---
id: project-setup-check
type: feature
title: Detect an out-of-date project setup on open and guide the update
status: backlog
priority: medium
size: M
created: 2026-10-05
updated: 2026-10-05
---

## Objective

When Safanoria changes what it installs in a project, every project that already uses it has to be migrated by hand, and nothing tells the people working there that they need to. The rename of the CLI from `safanoria` to `safanoria-cli` is the case that prompted this: projects were left with a pre-commit hook, a SessionStart hook and a skill that still call the old name, and each one has to be found and fixed separately.

Wanted: when a project is opened with the app (`safanoria`) or a `safanoria-cli` command is run in it, Safanoria checks whether what is installed in that project matches what this version would install, and if not, tells the user what is out of date and guides them through updating it.

What to check (at least):

- The git pre-commit hook (`Hooks.state` already tells `OUTDATED` from `SAFANORIA` and `FOREIGN`).
- The agent skill and the installed `SPEC.md` (what `safanoria-cli update` replaces).
- The SessionStart hook in `.claude/settings.json` and the CLAUDE.md paragraph.
- Ticket templates and the ticket README (missing or from an older version).
- `safanoria.yaml` itself: the `safanoria` spec version, and settings a newer version adds or renames.

How it should behave:

- The guidance names each out-of-date item and how to fix it, ideally one command (`safanoria-cli update`, `safanoria-cli hook install`) or one action in the app. What `update` treats as the project's own (templates, README, CLAUDE.md, `.claude/settings.json`) is still changed only after asking.
- A foreign pre-commit hook, or any file the project has edited on purpose, is not reported as out of date over and over: there must be a way to tell a deliberate difference from a stale one.
- The check must not get in the way: cheap enough to run on every command, silent when everything is current, on stderr so it doesn't break scripted output (`list`, `board`, `notes`), and absent from non-interactive runs such as the pre-commit hook and CI.
- The opposite case counts too: the project was set up by a newer Safanoria than the one installed, and it is the tool that needs updating.

To decide in the ticket: how a project records which version set it up (a stamp in `safanoria.yaml` or in the installed files, versus comparing contents as `Hooks.state` does), and whether per-machine things (the git hook lives in `.git`, not in the repository) are checked separately from what is committed and so already updated for teammates.

## Acceptance Criteria

## Work Log


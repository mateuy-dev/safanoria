---
id: after-release-tasks
type: feature
title: After Release section for post-release tasks
status: review
priority: medium
size: M
created: 2026-11-25
updated: 2026-11-25
---

## Objective

Some tickets still need a check or an admin task after they are released: running a migration or backfill in production, replying to the users who asked for the change (User Requests), checking a crash rate or metric some time after the release, updating a store listing. Today a ticket is `done` and stamped (`resolvedIn`) and nothing in it can say that work remains, so these tasks get lost.

Decision: add an optional body section `## After Release`, a checklist like Acceptance Criteria (§7.4), placed between Learnings and Work Log.

- It is written any time during work, as soon as the task is known.
- Items are checked on `mainBranch` (the real copy once merged, §14.1), each with a Work Log entry (e.g. `release · ran backfill on prod`).
- A ticket is settled when it is `done`, stamped, and every After Release item is checked. Its status stays `done`.
- Optionally, an item prefixed with a component (`app: …`) waits for that component's release, for multi-component tickets.

Rejected alternatives:
- A new status after `done` (e.g. `released` / `verifying`): `done` means merged (§6.1) and the stamp already records the release (§9); a new status duplicates that and complicates stamping, the parent Plan check rule and validation, which all key on `done`.
- A follow-up `related` maintenance ticket: works today, but is heavy for a short chore and isn't tied to the release; it sits in the backlog with no "do this once X ships" trigger.

Tooling built on it:
- Stamping (`safanoria-cli release`) prints the unchecked After Release items of the tickets it just stamped, as the release's to-do, like it prints the `fixes` lines.
- CLI/TUI/GUI: a view or filter of released tickets with pending after-release tasks.
- Validator: a warning (not an error) for After Release items on `wontfix` / `research` tickets.
- Tickets with `requests` almost always need "reply to the requesters": the template or Finish should suggest that item.

Spec changes: new §7.x section and a row in the §7 section table; a note in §9 for stamping; the warning in §12. Update templates, `skill/SKILL.md` and README.

## Acceptance Criteria

- [x] Release and dry-run output list unchecked tasks on the tickets being stamped, respecting component prefixes.
- [x] The CLI, TUI and GUI can filter tickets with tasks due after a release.
- [x] Validation warns about checklist items on research and wontfix tickets.
- [x] The spec, templates, skill and README describe how to record and complete follow-up work.

## Work Log

- **2026-11-25** · status · started

- **2026-11-25** · decision · Unprefixed tasks are due from the first stamp and appear again if still unchecked at a later stamp. A prefix naming a component in the ticket’s area waits for that component, so a partial release can surface its own tasks.
- **2026-11-25** · status · review

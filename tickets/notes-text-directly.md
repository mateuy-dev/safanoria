---
id: notes-text-directly
type: feature
title: Notes write the text directly
status: backlog
priority: low
size: M
created: 2026-10-05
updated: 2026-10-05
---

## Objective

Today `safanoria notes <component> <version> [<to>]` prints the tickets a version shipped (id, type, title, parent, Objective), and the text users read is written from that by an agent with the skill, or by hand (ticket `release-notes`). There is no command that prints the finished release notes.

Make `notes` able to print that text itself, so a release job or a person gets it without an agent. The text is for end users who update: in their words, non-technical, with everything they don't notice reduced to one "Bug fixing" line.

The CLI can't derive that wording from an Objective, so the text has to be in the ticket. One option: an optional `## Release Note` section (a SPEC change), one or two user-facing lines written when the ticket is finished; `notes` concatenates them, tells a parent and its children once, and turns tickets without one (`maintenance`, internal fixes) into the single "Bug fixing" line. Open: the language of the note when users read several, whether `finish` should ask for it, and whether this is worth it over asking the agent.

Not decided: we'll see whether to implement it.

## Acceptance Criteria

## Work Log


---
id: ticket-tags
type: feature
title: Tags to group tickets by theme
status: in-progress
priority: medium
size: M
created: 2026-10-05
updated: 2026-10-05
---

## Objective

Add an optional `tags` field to the ticket frontmatter so tickets that share an open-ended theme can be viewed together. Example from VacApp: several independent tickets concern the official registry integration, and today nothing lists them as a set.

No existing field covers this:

- `area` is tied to components (`app`, `ktor`, `rails`); a theme cuts across them.
- `parent` is a bounded piece of work: one level only, every child in its Plan, and it becomes `done`. A theme never finishes, and a themed ticket that is itself a parent could not belong to it.
- `related` is pairwise: grouping ten tickets needs a web of links or an arbitrary hub ticket.
- An id prefix (`registry-...`) gives one dimension only, cannot change later, and collides with the child naming convention (SPEC §3).

Proposed shape:

- `tags`: optional list of slugs, default `[]`, placed next to `area` in the frontmatter (SPEC §5).
- The allowed values are declared in `safanoria.yaml`, like `components`, `channels` and `refs`, as a map of slug to one-line description, e.g. `tags: { registry: Official registry integration }`. Validation rejects a tag that is not declared (SPEC §12). Free-form tags drift (`registry` vs `official-registry`), especially as agents create most tickets; the description is what the skill decides from when tagging a new ticket.
- Tags are for themes only, not for what `type` or `area` already say; the vocabulary should stay small.
- Tooling: filter by tag in the CLI ticket list and in the GUI board, and a way to set tags in `safanoria new`.

Touches SPEC.md (§2, §5, §12, and §13 for how the spec version is affected), both JSON schemas, the validator, the CLI, the GUI and the skill.

## Acceptance Criteria

## Work Log

- **2026-10-05** · status · started
- **2026-10-05** · decision · Spec stays at version 1: `tags` is an optional field old tools can ignore, and no valid ticket becomes invalid (§13). Named there as the example of such an addition.
- **2026-10-05** · decision · Without `tags` in `safanoria.yaml` every tag is `tag-unknown`: declaring is what stops drift, so there is no free-form mode. `new` refuses an undeclared tag too, before writing.
- **2026-10-05** · decision · A tag filter with several values matches any of them, like `--area` and `--type`; tickets show their tags as `#tag` in `list` and on board cards, so a theme is visible without filtering.
- **2026-10-05** · decision · The skill never invents a tag: a new theme is proposed to the user, who adds it to `safanoria.yaml`. Interactive `new` offers tags only inside the optional questions, since most tickets have none.

---
id: v1-tooling-schema
type: feature
title: JSON Schema for ticket frontmatter and safanoria.yaml
status: backlog
priority: high
size: S
created: 2026-10-01
updated: 2026-10-01
parent: v1-tooling
---

## Objective

A `schema/` directory with JSON Schemas for the ticket frontmatter (SPEC §5) and for
`safanoria.yaml` (SPEC §2). They are the machine-readable half of the spec: the CLI validates
with them, editors can use them for completion, and later apps share them.

The schemas cover only what a schema can express per file (types, enums, required fields,
id/date/version patterns). Cross-file rules (references, cycles, parent Plans, `area` against
`components`) stay in `validate`.

## Acceptance Criteria

- [ ] `schema/ticket.schema.json` and `schema/safanoria.schema.json` (JSON Schema 2020-12)
- [ ] Unknown fields are allowed (SPEC §2, §5)
- [ ] Example valid and invalid files under `schema/examples/`, checked by a test
- [ ] SPEC.md links to the schemas

## Plan

## Work Log

# Safanoria spec — version 1

Safanoria is a ticket tracker made of markdown files in the project's git repository.
One file is one ticket. The ticket is the context: whoever works on it, human or agent,
must be able to continue it from the file alone.

This document is the contract for every tool that reads or writes tickets (agents, CLI,
viewers, editors). The key words MUST, MUST NOT, SHOULD and MAY are used as in RFC 2119.

## 1. Repository layout

```
<repo>/
  safanoria.yaml              ← project configuration (§2)
  tickets/                    ← ticket directory (configurable)
    movement-animal-count.md  ← a ticket
    herd-locations.md
    attachments/<id>/...      ← optional files referenced from a ticket (§7.8)
    README.md                 ← ignored (not a ticket)
    _TEMPLATE.md              ← ignored (not a ticket)
```

A file in the ticket directory is a ticket if and only if its name is `<id>.md` and `<id>` is a
valid ticket id (§3). Every other file is ignored. Subdirectories other than `attachments/` are ignored.

## 2. Project configuration: `safanoria.yaml`

```yaml
safanoria: 1                          # spec version, required
dir: tickets                          # ticket directory, default "tickets"
mainBranch: master                    # branch that releases are made from, default "main"
worktree: ../VacAppKMP--{id}          # optional; where `start` creates a ticket's worktree
components:                           # required, at least one
  app:
    version: { file: composeApp/gradle.properties, property: appVersionName }
  ktor:
    version: { file: server/server/gradle.properties, property: serverVersionName }
  rails:
    external: true                    # released from another repository
channels: [whatsapp, email, phone, in-app, in-person]   # allowed requests[].channel
userRef: VacApp user id               # what requests[].user refers to (documentation)
refs:                                 # external systems tickets may reference (§5, refs)
  sentry: { url: "https://example.sentry.io/issues/?query={id}" }
learningTargets: [CLAUDE.md, docs/, .claude/skills/, code comment]   # optional hint (§7.6)
```

- `components` names are slugs (`^[a-z][a-z0-9-]*$`). They are the allowed values of `area`
  and the allowed keys of `resolvedIn`.
- `version` tells tools where the component's current version is read from. Supported forms:
  `{ file, property }` for `key=value` files, `{ file, regex }` with one capture group.
  `external: true` means the version is supplied when stamping (§9).
- `channels` defaults to `[email, phone, in-person, other]`.
- Unknown keys MUST be preserved by tools and MAY be ignored.
- [`schema/safanoria.schema.json`](schema/safanoria.schema.json) is the JSON Schema for this file.

## 3. Ticket id

The id is a short, human-readable slug. It is the filename, the git branch name and the
worktree name.

- MUST match `^[a-z][a-z0-9-]{2,39}$`, MUST NOT contain `--`, MUST NOT end with `-`.
- MUST be unique among all tickets, whatever their status. An id is never reused.
- SHOULD NOT clash with an existing branch when the ticket is created.
- MUST NOT change after creation. The title may change.
- Children SHOULD be named after their parent (`herd-locations` → `herd-locations-map-input`).

## 4. File structure

```markdown
---
<frontmatter: YAML, §5>
---

## Objective
...
```

The file is UTF-8, uses `\n` line endings, and starts with a YAML frontmatter block between
`---` lines, followed by the body (§7).

Dates (`YYYY-MM-DD`) and versions are strings. YAML 1.1 loaders (PyYAML, js-yaml's default
schema) turn an unquoted `2026-10-01` into a date object; tools MUST read it as a string.

## 5. Frontmatter

Fields SHOULD appear in the order below. Optional fields MAY be omitted; an omitted field has
its default value. Tools that write a ticket MUST preserve unknown fields.

[`schema/ticket.schema.json`](schema/ticket.schema.json) is the JSON Schema for the frontmatter.
It covers the rules that can be checked from one file; the rest of §12 needs the other tickets
and `safanoria.yaml`.

| Field | Required | Type | Default | Meaning |
|---|---|---|---|---|
| `id` | yes | id | | Equals the filename without `.md`. |
| `type` | yes | enum | | `feature` \| `bug` \| `maintenance` \| `research` (§6.2) |
| `title` | yes | string | | One line. |
| `status` | yes | enum | | `backlog` \| `ready` \| `in-progress` \| `review` \| `done` \| `wontfix` (§6.1) |
| `priority` | yes | enum | | `low` \| `medium` \| `high` \| `urgent` |
| `size` | yes | enum | | `XS` \| `S` \| `M` \| `L` \| `XL`. For `research`, the time box. |
| `area` | if >1 component | list of component | the only component | Components this ticket changes. |
| `assignee` | no | string | null | Who works on it, when it is not the usual person. |
| `created` | yes | date | | `YYYY-MM-DD` |
| `updated` | yes | date | | `YYYY-MM-DD`. Set on every change. |
| `parent` | no | id | null | §8.1 |
| `childrenMergeInto` | no | `parent` \| `main` | `parent` | Only on parents. §8.1 |
| `blockedBy` | no | list of id | `[]` | §8.2 |
| `related` | no | list of id | `[]` | §8.3 |
| `refs` | no | map: ref name → list of string | `{}` | Ids in external systems declared in `safanoria.yaml` `refs`, e.g. `sentry: [7Y, 7T]`. |
| `requests` | no | list of request | `[]` | §10 |
| `resolvedIn` | no | map: component → version | null | §9 |

A request is:

```yaml
- user: 1834            # project user reference (see userRef), or null
  who: Breeders association technician   # required when user is null; no personal data
  channel: whatsapp     # one of safanoria.yaml channels
  date: 2026-09-28
```

## 6. Status and type

### 6.1 Status

| Status | Meaning |
|---|---|
| `backlog` | Recorded, not prepared. |
| `ready` | Acceptance Criteria (and Plan, if planned) written and approved. |
| `in-progress` | Being worked on in its branch. |
| `review` | Work complete in its branch, waiting for review or merge. |
| `done` | Merged into its target (`mainBranch`, or the parent's branch). |
| `wontfix` | Closed without doing it. The Work Log says why. |

- A ticket starts work only when a human says so. Tools and agents MUST NOT move a ticket to
  `in-progress` on their own initiative.
- Moving forward MAY skip statuses (e.g. `backlog` → `in-progress` for a direct fix).
- Moving backward, and reopening a `done` or `wontfix` ticket to `backlog`, MUST add a Work Log
  entry with the reason.
- Content required by status is defined in §7.1.

### 6.2 Type

| Type | Meaning |
|---|---|
| `feature` | New or changed behaviour users see. |
| `bug` | Behaviour that is wrong. |
| `maintenance` | Work users don't see: migrations, dependency updates, refactors, CI. |
| `research` | Time-boxed investigation. The deliverable is Learnings, not merged code. |

`research` tickets MUST NOT have children, and never get `resolvedIn`. Work that follows from
them becomes new tickets linked with `related`.

## 7. Body

Sections are level-2 headings with these exact names, in this order:

| Section | Optional |
|---|---|
| `## Objective` | no |
| `## User Requests` | yes; present when `requests` is not empty |
| `## Acceptance Criteria` | no (may be empty in `backlog`) |
| `## Plan` | no (may be empty in `backlog`) |
| `## Design` | yes |
| `## Learnings` | yes |
| `## Work Log` | no (may be empty in `backlog`) |

Other `##` sections MAY be added. Tools MUST preserve them and their position.
Level-3 and deeper headings inside a section are free.

### 7.1 Required content by status

| Status | MUST be non-empty |
|---|---|
| `backlog` | Objective |
| `ready` | plus Acceptance Criteria |
| `in-progress` | plus Plan, Work Log |
| `review` | plus: every Plan item checked; every Learning resolved (§7.6) |
| `done` | same as `review` |

### 7.2 Objective

Free text: what we want and why. Context goes here.

### 7.3 User Requests

What users asked for, verbatim, in the original language. Never edited or paraphrased.
Append-only. One quote per entry in `requests`, in the same order:

```markdown
> M'agradaria poder exportar la llista d'animals.
— user 1834 · whatsapp · 2026-09-28

> Los técnicos necesitan filtrar los movimientos por fecha.
— Breeders association technician · in-person · 2026-09-12
```

The attribution line is `— <ref> · <channel> · <date>`, where `<ref>` is `user <user>` or the
request's `who`. Personal data MUST NOT appear (§10).

### 7.4 Acceptance Criteria

A checklist (`- [ ]` / `- [x]`) of observable outcomes, optionally followed by a line
`Out of scope:` and a plain list. For `research`, the items are questions to answer.

### 7.5 Plan

An ordered checklist. It is the task list: there is no separate task field.

- An item is checked when that step is done. The current step is the first unchecked one.
- Decisions and rejected alternatives SHOULD be written in the item they affect.
- In a parent ticket, an item that **starts with a ticket id in backticks** is a child:
  `` - [ ] `herd-locations-map-input`: riskiest, so early. `` Other items are the parent's own steps.
  A child item MUST be checked if and only if the child's status is `done` or `wontfix`.
- Items MAY be followed by indented continuation lines.

### 7.6 Learnings

Discoveries that are true beyond this ticket's Work Log: facts about the code, libraries or
external systems. Added when discovered. Each learning is a list item whose last line states
where it went:

```markdown
- maplibre-compose on desktop needs Java 25.
  → promoted: CLAUDE.md, docs/features/locationInput.md
- Demo map tiles are rate-limited.
  → new ticket: `map-tile-provider`
- The demo account has only sheep.
  → ticket only
```

A learning without a `→` line is pending. A ticket MUST NOT reach `review` with pending learnings.
"Promoted" means copied to where future work will read it (the project's `learningTargets`).

### 7.7 Work Log

Append-only, chronological. One entry per list item:

```markdown
- **2026-10-02** · step 2 · Deviation: movements already sent to the official registry must not change animals; added a criterion.
```

Format: `- **<YYYY-MM-DD>** · <ref> · <text>`, where `<ref>` is one of `plan`, `step <n>`,
a child id, `status`, `review`, `release`, or another single word. Entries SHOULD record
decisions and deviations from the Plan with their reasons, not restate the diff.

### 7.8 Attachments

Files a ticket needs (screenshots, logs, sample data) go in `<dir>/attachments/<id>/`, where
`<id>` is the ticket that owns them, and are referenced from it with relative links, which
render on GitHub and in apps:

```markdown
![Crash on save](attachments/movement-animal-count/crash.png)
```

- File names SHOULD be lowercase, without spaces.
- Each file SHOULD be under 1 MB: the repository keeps every version forever. Larger files
  (videos, dumps) go elsewhere and are linked by URL.
- §10 applies: crop or redact personal data from screenshots and logs.
- A ticket SHOULD link only to its own attachments.

## 8. Relations

Every relation is written on one side only. Tools derive the reverse direction.

### 8.1 `parent`

The ticket is part of a bigger ticket. A ticket is a **parent** when at least one ticket names
it in `parent`; there is no parent type.

- One level only: a parent MUST NOT have a `parent`.
- `research` tickets MUST NOT be parents.
- Every child MUST appear exactly once as a child item in the parent's Plan (§7.5), and every
  child item MUST name a ticket whose `parent` is this ticket. The Plan order is the suggested order.
- `childrenMergeInto: parent` (default): children branch from and merge into the parent's
  branch, and the parent merges into `mainBranch`, so the whole set ships together.
  `childrenMergeInto: main`: children merge into `mainBranch` independently.
- A parent is `done` when all its Plan items, children and own steps, are checked.

### 8.2 `blockedBy`

A real dependency: this ticket cannot be built or merged until those are `done`. MUST NOT form
cycles. MUST NOT be used to express mere preference; use the parent's Plan order for that.

### 8.3 `related`

See-also. No dependency. Use for work spun off during this ticket, the same area, or possible
duplicates.

## 9. Versions: `resolvedIn`

```yaml
resolvedIn:
  app: 4.3.0      # resolved in 4.3.0 and every later version, on every platform
  ktor: null      # not released yet
```

- `resolvedIn.<c>: <v>` means the change is in version `<v>` of component `<c>` **and every later
  version**. A platform that never received `<v>` has it from its first later version.
- Keys MUST be components in the ticket's `area`. Versions are `MAJOR.MINOR.PATCH`.
- `resolvedIn` is `null` until the ticket is `done`. `research` and `wontfix` tickets never have it.
- It is set only by stamping, never by hand. **Stamping** component `<c>` at version `<v>`: on
  `mainBranch`, for every ticket with `status: done`, `<c>` in `area`, and no `resolvedIn.<c>`,
  set `resolvedIn.<c>: <v>` and add a Work Log entry `release · <c> <v>`. Stamping SHOULD be part
  of the release commit.
- Because stamping reads `mainBranch`, children merged into a parent's branch are stamped together
  with the parent. A child's version MUST NOT be later than its parent's.
- A parent's own `resolvedIn` is stamped normally; the feature as a whole shipped at the highest
  version among the parent and its children.

## 10. Privacy

Tickets live in git, whose history is permanent and copied to every clone.

- `requests[].user` MUST be a pseudonymous reference (an id in the project's own system), never
  a name, email or phone number.
- `requests[].who`, quotes and all other text MUST NOT contain personal data of users.

## 11. Workflow

These rules apply to agents and to tools that automate work.

1. **Create.** Propose an id, check it is unused (file and branch), confirm it with the human,
   copy the template, set `status: backlog`, fill Objective. Add requests and quotes if it comes from users.
2. **Start** — only when the human says so. Create the branch `<id>` (from the parent's branch if
   `childrenMergeInto: parent`, else from `mainBranch`) and, if configured, the worktree. Set
   `status: in-progress`.
3. **Plan or fix**, as the human instructs:
   - *Plan*: read the code, write Acceptance Criteria and Plan, log `plan`, and wait for approval.
   - *Fix directly*: implement, but still write the Plan checklist and Work Log as you go.
   - No instruction: plan and wait.
4. **Work.** For each Plan item: implement, check it, add a Work Log entry if anything is worth
   recording, and commit code and ticket together with the message `<id>: <short description>`.
   Changes to the plan are written into the Plan and logged with the reason. Out-of-scope work
   becomes a new `backlog` ticket in `related`. Learnings are added when discovered.
5. **Finish.** Resolve every learning, run the project's tests, set `status: review`.
   Set `done` only when it is merged into its target. Never set `resolvedIn`.

## 12. Validation

A validator MUST report:

- Invalid id, filename/id mismatch, duplicate id.
- Missing required field; value outside its enum; malformed date or version.
- `area` or `resolvedIn` key that is not a component; `resolvedIn` key not in `area`.
- `resolvedIn` on a ticket that is not `done`, or on `research`/`wontfix`.
- References (`parent`, `blockedBy`, `related`, Plan child items, Learning `new ticket`) to
  unknown ids; `blockedBy` cycles; more than one level of parents; `research` with children.
- Parent Plan not listing each child exactly once; child item check state not matching child status.
- Section missing, out of order, or empty when required by status (§7.1).
- Unchecked Plan items or pending Learnings at `review`/`done`.
- `requests` and User Requests quotes not matching in count; `channel` not in `channels`.
- A child's `resolvedIn` later than its parent's.
- A link to a file under `attachments/` that does not exist.

A validator SHOULD warn about attachment files over 1 MB.

## 13. Versioning of this spec

`safanoria.yaml` declares the spec version. Additions that old tools can ignore (new optional
fields, new sections) keep the version. Changes that make valid tickets invalid, or change a
field's meaning, increase it.

The spec version is not the **Safanoria version**. Safanoria (this spec, the agent skill, the
ticket templates and the `safanoria` CLI) is released as one unit with one `MAJOR.MINOR.PATCH`
version, which says which spec versions it supports. A project records only the spec version.
Copies installed into a project (the skill, this file) SHOULD end with a
`<!-- safanoria X.Y.Z -->` line naming the Safanoria version they came from, so tools can tell
what is installed and update it.

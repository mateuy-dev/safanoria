---
id: release-sentry-fixes
type: feature
title: Print Sentry Fixes trailers for stamped tickets on release
status: in-progress
priority: medium
size: M
created: 2026-11-05
updated: 2026-11-05
requests:
  - user: null
    who: Project maintainer
    channel: other
    date: 2026-11-05
  - user: null
    who: Project maintainer
    channel: other
    date: 2026-11-05
---

## Objective

When `safanoria-cli release <component> <version>` stamps tickets, let Sentry mark their linked issues as resolved in that release. Tickets already reference Sentry issues through `refs: { sentry: [...] }` (SPEC §5, declared under `refs` in `safanoria.yaml`). Stamping and Sentry's "resolved in release" mean the same thing: if the issue happens again in a later release, Sentry reports it as a regression.

Chosen approach: commit trailers, with no Sentry API calls. `release` already leaves the commit to the user or CI. For each Sentry ref on a stamped ticket, it also prints a `Fixes <SHORT-ID>` line (in `--dry-run` too) to put in the release commit message. Sentry resolves the issues when the release is created with `sentry-cli releases set-commits` (its commit integration). Safanoria stays free of tokens, network calls and Sentry-specific code in `core`, and this also works for `external` components, whose release jobs stamp in the ticket repository.

Rejected: a generic `onRelease` command hook on each ref. It runs shell commands from the config, and the Sentry release usually doesn't exist yet when stamping happens. Also rejected: calling the Sentry API from the CLI. It needs a token, the org and a release-name mapping, and it puts HTTP and Sentry code into the multiplatform `core`.

To settle in the spec:
- Id format: the spec example uses short suffixes (`7Y`), but `Fixes` needs the full short id (`APP-7Y`). Either the ref config gets a project prefix (e.g. `refs.sentry.prefix`), or the spec requires full ids in tickets.
- Probably make this generic: a ref declares how a fixed id is written in a commit (e.g. `refs.<name>.fixes: "Fixes {id}"`), so other trackers with commit-message resolution (GitHub, Jira, Linear) work the same way and nothing in the code is specific to Sentry.
- Sentry release names are often `app@1.2.3`. With trailers this is the release job's concern (set-commits on the right release), but the docs should say so.

## User Requests

> Could we use the safanoria stamp to stamp also sentry bugs as fixed?
— Project maintainer · other · 2026-11-05

> yes, create a ticket for 1
— Project maintainer · other · 2026-11-05

## Acceptance Criteria

## Work Log

- **2026-11-05** · status · started

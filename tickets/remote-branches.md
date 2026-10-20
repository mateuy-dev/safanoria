---
id: remote-branches
type: feature
title: Read remote branches to see tickets being worked on another computer
status: in-progress
priority: medium
size: S
created: 2026-10-20
updated: 2026-10-20
---

## Objective

A ticket started on another computer has its real state (status, Plan, Work Log) only on branch `<id>` there. Once that branch is pushed, this computer only has it as a remote-tracking branch (`origin/<id>`), so the board, `list`/status and the validator still show the `mainBranch` copy (`backlog`) and the work in progress elsewhere is invisible; ids taken only on a remote branch can also be reused.

Read remote-tracking branches as SPEC §14.1 already allows ("Tools MAY include remote-tracking branches; a local branch wins over a remote one with the same name"), so a ticket being done on another computer shows its real status and is marked as remote. Decide whether to fetch (and how often) or only read what was last fetched, and whether starting a ticket that is already in progress remotely should warn or refuse.

## Acceptance Criteria

## Work Log

- **2026-10-20** · status · started

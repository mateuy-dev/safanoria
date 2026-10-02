---
id: cli-all-actions
type: feature
title: Powerful cli
status: backlog
priority: medium
size: S
created: 2026-10-02
updated: 2026-10-02
---

## Objective

Every action of the safanoria skill (create, plan, start, work, finish) should be available in the `safanoria` CLI, so a person can work on tickets by hand without an agent.

Before starting, we must agree on which operations the CLI offers: this ticket begins with that discussion, recorded here.

Replaces the `cli-start-command` ticket (deleted before it was committed). Its idea is one of the operations to discuss: `safanoria start <id>` creates the branch `<id>` (from the parent's branch when the parent has `childrenMergeInto: parent`, otherwise from `mainBranch`), creates the worktree when `safanoria.yaml` has `worktree`, sets `status: in-progress` and logs `status · started`. Then the skill calls the CLI instead of repeating the steps.

## Acceptance Criteria

## Plan

## Work Log


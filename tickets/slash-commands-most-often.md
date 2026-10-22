---
id: slash-commands-most-often
type: feature
title: Slash commands for the most often run workflows
status: backlog
priority: medium
size: S
created: 2026-10-22
updated: 2026-10-22
---

## Objective

Many workflows are typed to Claude over and over in nearly the same words, e.g. "create a new ticket that does X, and open a Herdr session for it". Define Claude Code slash commands (skills with disable-model-invocation) for the most frequent ones, so each is one short command with the ticket text as argument. Example: /scish (safanoria create issue, start herdr) = create the ticket with the safanoria skill, commit it on main, then run safanoria-cli start <id> --herdr. First find which workflows are actually run most often (past sessions, the skill's flows: create, create+start, review, finish review, merge), then pick short memorable names. Decide whether they ship with the safanoria skill (so every project using Safanoria gets them) or stay personal/project-local.

## Acceptance Criteria

## Work Log


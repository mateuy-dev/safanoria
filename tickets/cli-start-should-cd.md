---
id: cli-start-should-cd
type: feature
title: CLI start should cd to the created worktree
status: backlog
priority: medium
size: S
created: 2026-10-02
updated: 2026-10-02
---

## Objective

After `safanoria start <id>` creates the ticket's worktree, the user wants to end up in it, so they can open a session there right away instead of copying the path and running `cd` by hand.

A child process can't change its parent shell's working directory, so this needs a shell-side piece: e.g. a shell function/wrapper (installed by `init` or documented in the README) that runs `safanoria start` and then `cd`s to the worktree path it prints, or a flag such as `--print-path` for `cd "$(safanoria start <id> --print-path)"`, or spawning a subshell in the worktree. Pick the approach that keeps `start` scriptable and works in bash/zsh.

## Acceptance Criteria

## Work Log


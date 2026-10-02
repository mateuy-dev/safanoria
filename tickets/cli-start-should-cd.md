---
id: cli-start-should-cd
type: feature
title: CLI start should cd to the created worktree
status: review
priority: medium
size: S
created: 2026-10-02
updated: 2026-10-03
---

## Objective

After `safanoria start <id>` creates the ticket's worktree, the user wants to end up in it, so they can open a session there right away instead of copying the path and running `cd` by hand.

A child process can't change its parent shell's working directory, so this needs a shell-side piece: e.g. a shell function/wrapper (installed by `init` or documented in the README) that runs `safanoria start` and then `cd`s to the worktree path it prints, or a flag such as `--print-path` for `cd "$(safanoria start <id> --print-path)"`, or spawning a subshell in the worktree. Pick the approach that keeps `start` scriptable and works in bash/zsh.

## Acceptance Criteria

- [x] `safanoria start <id> --print-path` prints only the directory to work in (the worktree, or this checkout once switched) on stdout; all other output goes to stderr
- [x] The README documents a bash/zsh shell function that starts a ticket and `cd`s into it

## Work Log

- **2026-10-03** · status · started
- **2026-10-03** · decision · `--print-path` flag plus a README shell function, rather than `init` installing a function into shell rc files (touching the user's dotfiles is out of `init`'s scope) or spawning a subshell (nests shells, not scriptable). Messages go to stderr so stdout is only the path; without a worktree it prints the checkout root, even when uncommitted changes kept it from switching. `--no-switch` with `--print-path` is a usage error (no directory to go to); `--dry-run` prints no path.
- **2026-10-03** · status · review

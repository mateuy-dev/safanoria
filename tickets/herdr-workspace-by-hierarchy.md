---
id: herdr-workspace-by-hierarchy
type: feature
title: Herdr workspace or tab by ticket hierarchy and size
status: review
priority: medium
size: S
created: 2026-11-03
updated: 2026-11-03
requests:
  - user: null
    who: Project maintainer
    channel: other
    date: 2026-11-03
---

## Objective

When a ticket is opened in Herdr (`start --herdr`, the `/ssh*` and `/scsh*` skills, and the TUI/GUI opening an agent on a ticket), the choice between a new workspace and a tab of the current workspace is today made by size alone: L and XL get a workspace, everything else (and unsized) a tab (`StartCommand.kt`, `tui/.../Workplace.kt`).

Change the rule to depend on where the ticket sits in the hierarchy:

- **Parent ticket** (has children): always a workspace, whatever its size. A parent hosts the work of several children, so it deserves its own space.
- **Child ticket** (has `parent`): a tab if XS, S or M; a workspace if L or XL (same as today).
- **Other tickets** (no parent, no children): a tab only if XS; S, M, L and XL get a workspace.

A ticket that is both a child and a parent counts as a parent. A ticket without `size` keeps opening in a tab unless it is a parent. `start --in workspace|tab` still overrides the rule. The rule applies to everything that opens a ticket in Herdr, including the TUI's Open shell (no agent): today the TUI only applies the size rule when starting an agent, which was inherited from `start --herdr` (always an agent) rather than decided, and it scatters a ticket's panes across workspaces. The decision should live in one place in core, shared by the CLI and the TUI, and README/SPEC/help text describing the size rule must be updated.

## User Requests

> Lets change this. If child tickets tab if XS, S or M. Parent tikets allways workspace. Others only tabs if XS
— Project maintainer · other · 2026-11-03

## Acceptance Criteria

- [x] `Herdr.opensWorkspace` in core decides workspace or tab: parent always; child when L or XL; other unless XS; unsized non-parent a tab
- [x] `start --herdr` uses it; `--in workspace|tab` still overrides
- [x] The TUI uses it for agents and for the plain shell (Start, Create worktree, Open shell), and its confirmation says workspace or tab accordingly
- [x] README and `start` help describe the new rule

## Work Log

- **2026-11-03** · decision · Open shell follows the same workspace/tab rule as agent sessions: the shell-always-tab behavior had no recorded reason, and a ticket's panes should live together in its workspace.
- **2026-11-03** · status · started
- **2026-11-03** · decision · A child is a ticket with `parent` in its frontmatter (even if the parent file is missing); a parent is one with children in the graph. A ticket not in the graph opens in a tab. The GUI is unaffected: it opens a desktop terminal, not Herdr.
- **2026-11-03** · status · review


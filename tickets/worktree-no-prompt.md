---
id: worktree-no-prompt
type: feature
title: Worktrees under .claude/worktrees so switching into them needs no permission prompt
status: wontfix
priority: low
size: S
created: 2026-10-02
updated: 2026-10-02
---

## Objective

When a ticket starts, the agent switches into its worktree with `EnterWorktree`. Ticket
worktrees are created next to the repository (`../safanoria--<id>`, following the SPEC example).
Claude Code asks for permission every time it switches into one of them: "permission-root
relocation to … a model-supplied worktree outside .claude/worktrees/". Worktrees under
`<repo>/.claude/worktrees/` do not get this prompt. We want to switch into ticket worktrees,
including child tickets' worktrees, without being asked each time.

An `allow` rule for `EnterWorktree` probably does not skip this prompt, because the check is on
the path. Moving the worktrees is what fixes it.

## Acceptance Criteria

- [ ] Starting a ticket creates its worktree and switches the session into it with no
      permission prompt
- [ ] Worktrees under `.claude/worktrees/` are ignored by git
- [ ] The skill's Start step and SPEC.md's settings example recommend `.claude/worktrees/{id}`,
      and say why
- [ ] The git commands that Start runs do not prompt either (shared `.claude/settings.json`)

## Plan

- [ ] `safanoria.yaml`: `worktree: .claude/worktrees/{id}`; `.gitignore`: `.claude/worktrees/`
- [ ] `.claude/settings.json`: allow `Bash(git worktree add:*)`, `Bash(git worktree list:*)`,
      `Bash(git branch:*)`, `Bash(git switch:*)`, `Bash(git checkout:*)`, `EnterWorktree`,
      `ExitWorktree`
- [ ] `skill/SKILL.md` Start step 2 and SPEC.md settings example: recommend the path under
      `.claude/worktrees/`, keep `../<project>--{id}` as an allowed alternative
- [ ] Check by hand: start a throwaway ticket, confirm there is no prompt, then clean up
- [ ] Optional, only with the user's OK: `git worktree move` the existing sibling worktrees

## Work Log

- **2026-10-02** · status · Created from a user question about the permission prompts they get
  when the agent switches into a ticket's worktree.
- **2026-10-02** · status · Obsolete: sessions no longer switch into ticket worktrees with EnterWorktree. The user runs `safanoria start` and opens a session in the worktree, so there is no switch to prompt for.

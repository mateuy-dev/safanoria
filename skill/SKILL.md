---
name: safanoria
description: >
  Create, plan and work on Safanoria tickets: markdown files in the project's ticket directory
  (see safanoria.yaml). Use when the user asks to create a ticket, log a user request, plan or refine
  a ticket, start / fix / work on / continue a ticket (by id or title), or finish one; or when working
  on a branch whose name is a ticket id.
---

The format is defined in `SPEC.md` next to this file. Read it before writing a ticket and follow
it exactly. Project settings (ticket directory, components, channels, worktree path, main branch)
are in `safanoria.yaml` at the repository root.

## Rules that are easy to get wrong

- **Never start work on your own.** Move a ticket to `in-progress` only when the user tells you to
  start or fix it.
- The ticket is the context. Decisions, rejected alternatives and deviations go into the ticket,
  not only into the chat.
- Set `updated` to today on every change. Keep frontmatter field order. Preserve unknown fields and sections.
- **Never set `resolvedIn`.** Release stamping does that (`safanoria release <component>`, run
  by the project's release process, not while working on a ticket).
- **No personal data**: `requests[].user` is the project's user id; never names, emails or phones.
  Quotes are verbatim, in the original language.
- User Requests and Work Log are append-only.

## Create

With the `safanoria` CLI installed (`safanoria version` works), steps 1, 2 and 4 are:
`safanoria new "<title>" [--parent <id>] [--type …] [--area …] --dry-run` to get a suggested
id and see what it would write, confirm the id with the user, then the same command without
`--dry-run` (add `--id <id>` if the user chose another one, `--objective "…"` to fill it).
Then do step 3 by hand, and run `safanoria validate <file>`. Without the CLI:

1. Propose an id (SPEC §3). Check `<dir>/<id>.md` does not exist and
   `git branch -a --list '*<id>'` is empty. Confirm the id with the user.
2. Copy the template for the type (SPEC §6.2: `<dir>/_TEMPLATE.<type>.md`, else
   `<dir>/_TEMPLATE.md`, else `templates/<type>.md` or `templates/ticket.md` from Safanoria), set
   `status: backlog`, fill Objective (for a bug, its Steps to reproduce, Expected and Actual).
3. If it comes from a user: add a `requests` entry and the verbatim quote in User Requests.
4. If it belongs to a bigger ticket: set `parent`, and add the child item to the parent's Plan.

## Start

Only when the user says so.

1. Branch `<id>`: from the parent's branch if the parent has `childrenMergeInto: parent`
   (the default), otherwise from `mainBranch`.
2. If `safanoria.yaml` has `worktree`, create the worktree there (`{id}` replaced) and work in it:
   - `git worktree add` does not move the session. Switch into it (Claude Code: `EnterWorktree`
     with `path`); otherwise you keep editing the original checkout. If the session is already
     in another worktree (e.g. the parent's), `ExitWorktree` with `keep` first: `EnterWorktree`
     refuses to jump from one external worktree to another.
   - Uncommitted changes in the original checkout are not in the worktree. If the ticket needs
     them, copy them over and say so in the Work Log.
   - `.claude/settings.local.json` is ignored by git, so it is missing in the worktree. If the
     user relies on local settings (permissions, extra directories), tell them to recreate it.
3. Set `status: in-progress` and log `status · started`.
4. Then do what the user asked:
   - **Plan**: read the relevant code; write Acceptance Criteria and Plan (a checklist, one item
     per commit-sized step, decisions inline); log `plan`; stop and wait for approval.
   - **Fix directly**: implement, still writing the Plan checklist and Work Log as you go.
   - **No instruction**: plan and wait.

## Work

For each Plan item, in order:

1. Implement it, following the project's CLAUDE.md.
2. Check the item. Add a Work Log entry if there is a decision or deviation worth recording.
3. Commit code and ticket together: `<id>: <short description>`.

Along the way:
- Plan wrong? Update Plan / Acceptance Criteria and log why.
- Found out-of-scope work? Create a new `backlog` ticket and add it to `related`.
- Discovered something true beyond this ticket? Add it to Learnings right away (SPEC §7.6).

## Finish

1. Resolve every learning: promote it (to the project's `learningTargets`: CLAUDE.md, docs, a skill,
   or a code comment next to the code), turn it into a new ticket, or mark it `ticket only`.
2. Run the project's tests for the touched components, and `safanoria validate` if the CLI is
   installed.
3. Set `status: review` and log it.
4. Set `done` only when the user says it is merged into its target.
5. On a parent: when a child becomes `done`, check its item in the parent's Plan.

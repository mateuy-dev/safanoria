---
name: safanoria
description: >
  Create and keep Safanoria tickets: markdown files in the project's ticket directory (see
  safanoria.yaml) that track the backlog and the progress of each feature. Use when the user asks
  to create a ticket or log a user request, to finish a ticket, or what a version brings (release
  notes, what changed between two versions); and in any session on a branch whose name is a
  ticket id, to keep that ticket current.
---

The format is defined in `SPEC.md` next to this file; read it before writing a ticket. Project
settings (ticket directory, components, channels, worktree path, main branch) are in
`safanoria.yaml` at the repository root.

Tickets record what is wanted and what was decided. They don't script the work: there is no
planning or approval step, and a session works as the user directs it.

## Rules

- **Never start a ticket.** The user starts tickets with `safanoria-cli start <id>` and opens a
  session in the worktree. Don't create ticket branches or worktrees, don't switch the session
  into another worktree, and don't set `in-progress` yourself.
- Set `updated` to today on every change. Keep frontmatter field order. Preserve unknown fields and sections.
- User Requests and Work Log are append-only.
- **Never set `resolvedIn`.** The project's release process does that (`safanoria-cli release`).
- **No personal data**: `requests[].user` is the project's user id; never names, emails or phones.
  Quotes are verbatim, in the original language.

## Create

1. Write the ticket from the conversation. The Objective is the most valuable part: what is
   wanted and why, with the context a later session will need. For a bug, also Steps to
   reproduce, Expected and Actual. Acceptance Criteria only if they are clear already.
2. With the CLI, `safanoria-cli` (`safanoria-cli version` works): `safanoria-cli new "<title>" --objective "…"
   [--type …] [--area …] [--tag …] [--size …] [--parent <id>] [--on <branch>]`. It picks the id, fills the
   template and validates. Don't ask the user to confirm the id: tell them which one it got,
   and rename it if they ask (rename the file, its `id`, and any references; fine while the
   ticket is only on one branch).
   **Tags**: if `safanoria.yaml` has `tags`, add with `--tag` those whose description fits the
   ticket; most tickets have none. Never a tag that isn't declared there: a new theme is the
   user's call, so suggest it instead.
3. **Which branch** (SPEC §14.2):
   - A top-level ticket goes on `mainBranch`. In a session on another branch, use `--on <mainBranch>`:
     it commits the ticket there without touching this checkout. If that fails (e.g. `main` is
     protected), create it on the current branch and tell the user it needs moving.
     The commit is on the local `mainBranch` only. If this branch references the ticket
     (`related`), tell the user to push `mainBranch` with it: until then `validate` warns
     `ref-unpushed`, and CI on this branch fails with `ref-unknown`.
   - A child of the current ticket goes on this branch, without `--on`.
   - Don't edit, from another branch, a ticket you created on `mainBranch`: the file isn't there.
4. From a user: add the `requests` entry and the verbatim quote in User Requests (SPEC §7.3).
   With `--on`, edit and commit that where `mainBranch` is checked out (`git worktree list`;
   `git -C <worktree> commit -- <file>`), or ask the user.
5. `safanoria-cli validate <file>`.
6. **Commit it**, when it was created on this checkout (without `--on`, which commits by itself)
   and you didn't have to ask the user anything to write it: `git add <files>`, then
   `git commit -m "<id>: create" -- <files>`. The files are the ticket, with its `requests` entry
   and quote, and for a child the parent, whose Plan got the item; nothing else that is
   uncommitted here. If you asked something, its content is still under discussion: leave it
   uncommitted and tell the user.

Without the CLI: pick an id (SPEC §3) that no branch has used (`git rev-list --all -1 --
'<dir>/<id>.md'` and `git branch -a --list '*<id>'` print nothing), copy the template (SPEC §6.2),
set `status: backlog`, today's dates, and for a child `parent` plus its item in the parent's Plan.

## Working on a ticket's branch

When the current branch is a ticket id (a SessionStart hook usually gives you the ticket; else
read `<dir>/<id>.md`), the session belongs to that ticket. Work normally, as the user directs.
Along the way:

- **Decisions** go into the Work Log: `- **<date>** · decision · <what and why>`. One or two
  lines, for choices a later reader would wonder about: an approach taken or rejected, a
  deviation from the Objective, a constraint found. Not progress, not a summary of the diff.
  Commit the entry together with the code it explains.
- **The goal changed?** Update Objective or Acceptance Criteria and log why.
- **Out-of-scope work**: a new `backlog` ticket on `mainBranch` (Create, `--on`), added to this
  ticket's `related`.
- **Something true beyond this ticket** (about the code, a library, an external system): add it
  to Learnings (SPEC §7.6), and promote it to the project's `learningTargets` when it is clear.
- A Plan is optional. Write one only if the user asks or it helps; a parent's Plan lists its children.

## Finish

When the user says the work is done: resolve pending Learnings if you can, check the Acceptance
Criteria that are met, run the project's tests and commit everything. Then `safanoria-cli finish
<id>` (or set `status: review`, log `status · review` yourself, and commit). It refuses on
uncommitted changes and on a branch behind its target: commit, or merge the target in, and run
it again. It lists what the ticket still has open; tell the user.

The user reviews. What comes next is theirs to say, never yours to start:

- **"Merge it" / "land it"**: `safanoria-cli merge <id>`. One merge commit on the target with the
  ticket `done` in it and the parent's Plan item checked; then it removes this worktree and
  deletes the branch, so it is the last thing the session does. It pushes nothing. If it
  refuses (a conflict, uncommitted changes), fix that on the branch and tell the user.
- **The review found something**: `safanoria-cli reopen <id> --reason "<what>"`, then work on it
  here as before.
- **Merged elsewhere** (a pull request): `safanoria-cli finish --done` sets `done` on every ticket
  merged but still in review, and removes their worktrees and branches.

Without the CLI: merge with `git merge --no-ff --no-commit <id>` on the target, set `status: done`
and log `status · done` (and check the item in the parent's Plan) before committing the merge.

## Release notes

When the user asks what a version brings, or what changed between two versions:
`safanoria-cli notes <component> <version>` prints the tickets stamped with that version
(`resolvedIn`, SPEC §9) and their Objective; `safanoria-cli notes <component> <from> <to>` those
after `<from>` (which the user already has) up to and including `<to>`. Without the CLI: the
tickets whose `resolvedIn.<component>` is in that range.

Write the text from them. Its readers are the people who use the product and are updating it:

- What they can now do, or what no longer goes wrong, in their words and their language. No
  ticket ids, no code, library or screen-internal names, nothing about how it was done.
- One entry per change the user notices: a parent and its children are one feature, told once.
  New things first, then fixes. Short; a version is a few lines.
- Whatever the user doesn't notice (`maintenance`, refactors, internal fixes) is not described:
  all of it together is one last line, "Bug fixing". If nothing else shipped, that line is the text.
- Between two versions, one text for the whole update, not one per version.
- Only what the tickets say. If an Objective doesn't tell what changed for the user, read the
  ticket; if it still doesn't, ask.

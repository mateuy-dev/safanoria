# Safanoria

Tickets as markdown files in the project's own repository: the backlog and the progress of
every feature, tracked in git next to the code.

The way of working is deliberately plain. You ask an agent session to create tickets. To work
on one, you start it with the CLI (a branch and a worktree) and open an ordinary Claude Code
session in that worktree. The session knows its ticket from the branch, works as you direct it,
and records its decisions in the ticket as it commits. No planning or approval steps.

- [`SPEC.md`](SPEC.md): the format, version 1. The contract for every tool.
- [`skill/`](skill/): agent workflow (Claude Code skill). `safanoria-cli init` / `update` install it,
  with `SPEC.md`, into the project's `.claude/skills/safanoria/`.
- [`templates/`](templates/): blank tickets. `ticket.md` for any type (copy to
  `<dir>/_TEMPLATE.md` to customise it), `bug.md` and `research.md` for those types (copy to
  `<dir>/_TEMPLATE.<type>.md`). The CLI embeds them.
- [`schema/`](schema/): JSON Schemas for ticket frontmatter and `safanoria.yaml`, with valid and
  invalid examples (checked by `core`'s tests). The CLI embeds them.

For completion and checks in editors with the YAML language server (VS Code YAML extension,
IntelliJ), start `safanoria.yaml` with:

```yaml
# yaml-language-server: $schema=https://raw.githubusercontent.com/mateuy-dev/safanoria/main/schema/safanoria.schema.json
```

Ticket frontmatter is inside markdown, which editors don't check against a schema; use
`safanoria-cli validate` for tickets (below).

There are two commands. `safanoria` opens the desktop app on the project you are in: a board
of the tickets you can click through (see "The desktop app"). `safanoria-cli` is the
command-line tool: it does the mechanical parts, so agents and people spend their time on the
content. `safanoria-cli --help` lists its commands; each is described below.

| Command | Does |
|---|---|
| `init`, `update` | Set up a project; install or update the skill, SPEC.md and templates |
| `new` | Create a ticket from a title: id, template, parent's Plan |
| `list`, `board` | One line per ticket; a markdown board by status, done split in to release and released |
| `start`, `finish` | Start a ticket (branch, worktree, `in-progress`); set it `review` when the work is complete |
| `merge`, `reopen` | Land a ticket in review (merged, `done`, worktree and branch removed); or send it back to `in-progress` |
| `context` | The current branch's ticket, for the agent session (SessionStart hook) |
| `validate`, `hook` | Check every SPEC rule; before each commit |
| `release` | Stamp `resolvedIn` on the tickets a release ships |
| `notes` | The tickets a version shipped, to write its release notes from |

## Installing

```sh
curl -fsSL https://raw.githubusercontent.com/mateuy-dev/safanoria/main/install.sh | sh      # Linux x64, macOS arm64
irm https://raw.githubusercontent.com/mateuy-dev/safanoria/main/install.ps1 | iex           # Windows x64 (PowerShell)
```

Releases start at `v0.1.0`; until it is published, use `make install` from a checkout.
The scripts download the latest release (`SAFANORIA_VERSION=0.1.0` for another), check its
checksums, and put `safanoria-cli` and `safanoria` in `~/.local/bin` (`SAFANORIA_BIN_DIR` to
change it). The app comes with its own Java runtime (about 150 MB), in
`~/.local/share/safanoria` (Windows: `%LOCALAPPDATA%\safanoria`; `SAFANORIA_APP_DIR` to change
it); `SAFANORIA_CLI_ONLY=1` installs only the CLI, for CI and servers. The Linux CLI needs
`libunistring.so.5` (Ubuntu 24.04+, Debian 13+: package `libunistring5`). From a checkout:
`make install`.

**Updating from 0.1.** The command-line tool was `safanoria` then; that name now
opens the app. The install scripts replace the old binary. Hooks set up before still call the
old name, so in each project (and each clone, for the git hook) run:

```sh
safanoria-cli update          # the SessionStart hook in .claude/settings.json, the skill
safanoria-cli hook install    # the git pre-commit hook
```

Until then nothing runs a command by mistake: `safanoria validate --staged` and the like open no
window, fail, and say to use `safanoria-cli`. So a commit with the old git hook is refused with
that message, and the old SessionStart hook gives sessions no ticket. Rename the command in your
own scripts and CI too. Workflows that use the Action at an older tag keep working.

## Adding Safanoria to a project

```sh
safanoria-cli init                      # asks for the components (parts released with their own version)
safanoria-cli init --component app=composeApp/gradle.properties:appVersionName --external rails
safanoria-cli update                    # later: this version's skill and spec, and any new templates
```

`init` writes `safanoria.yaml`, the ticket directory (`_TEMPLATE.md`, `_TEMPLATE.bug.md`,
`_TEMPLATE.research.md`, `README.md`), the skill and SPEC.md in `.claude/skills/safanoria/`,
the paragraph that points agents to the skill in `CLAUDE.md`, and the SessionStart hook in
`.claude/settings.json` (see "Working on a ticket"). The skill and spec are Safanoria's and end
with `<!-- safanoria X.Y.Z -->`; `update` replaces them and says which version was there before.
Templates, the ticket README, `CLAUDE.md` and `.claude/settings.json` are the project's: they
are created when missing and changed only after asking (`--yes` to accept, `--dry-run` to see).

Then make the release process run `safanoria-cli release <component>` (see "Releasing"; SPEC §9).

## Creating tickets

Usually you ask the agent ("create a ticket for…", "log this user request"), in any session:
the main checkout for backlog work, or a ticket's worktree when the work turns up there. It
writes the Objective from the conversation, picks the id and tells you, and runs `new` for the
rest. Ask it to rename the id if you don't like it: before the ticket is started that's cheap.
The command, which you can use too:

```sh
safanoria-cli new "Herd photos from the field" --dry-run   # suggested id, files it would write
safanoria-cli new "Herd photos from the field" --id herd-photos --size M --objective "Why…"
safanoria-cli new "Send movements" --tag registry             # a theme declared in safanoria.yaml tags
safanoria-cli new "Map pin" --parent herd-locations          # child: herd-locations-map-pin, added to the parent's Plan
safanoria-cli new "Export is slow" --on main                 # from another branch: committed on main, this checkout untouched
```

`new` fills the template for the type (`<dir>/_TEMPLATE.<type>.md`, else `<dir>/_TEMPLATE.md`,
else the built-in one, which for bugs has Steps to reproduce, Expected and Actual; `--objective`
keeps those subsections) with the id, title, type, priority, size, `area` (required when there
are several components), `tags`, `status: backlog` and today's date. It refuses a tag that
`safanoria.yaml` doesn't declare, an id that exists on
any branch (ids are never reused), an unknown parent, and a parent that can't have children; a
branch with the same name is a warning. The suggested id is short (filler words dropped, at
most four words); it's the branch name.

**On a terminal, the CLI asks** for what you leave out. `safanoria-cli` alone asks which command to
run. `new` without a title asks for the title, the type and the id (enter keeps the suggestion),
optionally priority, size, parent, tags (when the project declares some) and objective, and, on a branch other than `mainBranch`,
whether the ticket goes on `mainBranch`; with a title it asks only for a missing `area`.
`start`, `finish`, `merge`, `reopen` and `release` without an id or component offer a list (backlog and ready
tickets to start; in-progress ones to finish; those in review to merge or reopen, and `reopen` asks for the reason). `init` asks for the
components and where each one's version is, and `init` and `update` ask before changing a file
of yours. Ctrl-C cancels without changing anything. When stdin or stdout isn't a terminal (scripts, agents, CI), nothing is asked:
a missing value is an error, as before.

**Which branch** (SPEC §14.2): a new top-level ticket belongs on `mainBranch`, even when you
find the work while on another ticket's branch. On `main` the id is taken at once, the ticket
survives if that branch is abandoned, and it can be prioritized on its own. `--on main` does
that from anywhere:
- If `main` is checked out in another worktree, it writes the file there and commits only that
  file; whatever else is staged there stays staged.
- Otherwise it commits straight to the branch, without checking it out.

It validates the ticket first, and the commit is `<id>: create`. Children are created on their
parent's branch, which is where you are while planning the parent. Without `--on`, `new` writes
into this checkout and doesn't commit; on a branch other than `mainBranch` it reminds you about
`--on`.

**From a phone**, without building anything: with Claude Code's
[Remote Control](https://code.claude.com/docs/en/remote-control), the Claude app drives a
session on your computer, so the skill and `--on main` work as they do at the desk. A photo
or screenshot attached in the app is saved under `~/.claude/uploads/`, and the agent can copy it
into `<dir>/attachments/<id>/` (SPEC §7.8). Crop or draw over personal data on the phone before
attaching it. A cloud session works too, but it commits on its own branch, which then needs
merging into `mainBranch`.

## Viewing tickets

```sh
safanoria-cli list                                  # one line per ticket, in-progress first
safanoria-cli list --status ready,backlog --type bug
safanoria-cli list --tag registry                    # every ticket of a theme (safanoria.yaml tags)
safanoria-cli list --parent v1-tooling --blocked    # children blocked by a ticket that is not done
safanoria-cli list --format json                    # {tickets: [...]}: every field, plus children, blocks, openBlockers, progress
safanoria-cli board                                 # markdown board on stdout
safanoria-cli board -o docs/BOARD.md                # links relative to the file
safanoria-cli list --remote                         # also origin/* branches (git fetch first)
safanoria-cli list --checkout                       # only this checkout's files
```

**Every branch.** A ticket you start moves to its own branch: there it becomes `in-progress`
and gets its Work Log, while `main` keeps the `backlog` copy until the merge. So `list` and
`board` read every local branch, without checking any out, and show each ticket's real copy
(SPEC §14.1):
- The copy on branch `<id>` while that branch isn't merged.
- A child's copy on its parent's branch, once merged there.
- Otherwise `main`'s copy.

A branch checked out in a worktree is read from its files, so uncommitted edits show. A ticket
that exists only on some other branch (created there instead of on `main`) is listed with
`only on <branch>`. Branches already merged into `main` aren't read: their tickets are on `main`.
`list --format json` has `branch` (where the copy came from) and `onlyOnBranch` for each ticket.
Outside git, or without a local `mainBranch` branch (as in a CI checkout of one commit), both
read the checkout as it is.

`list` and `board` show tickets in the same order: status (in-progress, review, ready, backlog,
done, wontfix), then priority, then id. In `board`, children appear under their parent with
the parent's `done/total` Plan items (children and own steps). Done tickets are in two sections:
**To release**, those `release` would stamp (a component of their `area` has no `resolvedIn`
yet), and **Released**, with their versions. The board has no dates, so a committed one only
changes when tickets do. For example:

```markdown
## In progress (1)

- [herd-locations](tickets/herd-locations.md) Show where each herd is (high) · 2/4
  - [x] [herd-locations-model](tickets/herd-locations-model.md) Store herd locations
  - [ ] [herd-locations-map](tickets/herd-locations-map.md) Herds on a map (in-progress)
  - [ ] [herd-locations-sync](tickets/herd-locations-sync.md) Sync locations to the server (backlog) · blocked by [herd-locations-map](tickets/herd-locations-map.md)

## Backlog (1)

- [delete-birth-crash](tickets/delete-birth-crash.md) Deleting a birth crashes (bug, urgent)
```

### The desktop app

`safanoria` opens the desktop app: the same tickets as a board you can click through.

```sh
safanoria                    # the project of the working directory
safanoria /path/to/project   # or of another directory
```

The prompt comes back at once and the window stays open when the terminal is closed. Nothing the
app prints shows then: `SAFANORIA_FOREGROUND=1 safanoria` keeps it attached to the terminal, to
see its output (a crash). In a directory that is in no Safanoria project, a window says so.

- **Board**: a column per status, left to right as a ticket moves (Backlog, Ready, In progress,
  Review, To release, Released, Won't fix). Done tickets are in two columns: To release is what
  the next version brings, Released what a version already has. Every ticket is a card, children
  too, read from every local branch as `list` does. Released and Won't fix start collapsed: click
  a column's header to collapse or open it. Chips filter by type, area, tag and blocked. A card shows its parent, its tags, a parent's
  `done/total` Plan items, open blockers, and how many problems `validate` finds in it.
- **Ticket**: the body as rendered markdown, its fields, links to its parent, children and
  blockers, and its `validate` problems. Problems are those of this checkout's files, so a
  ticket shown from another branch shows none.
- **Actions**, in the bar under the ticket (all but Open terminal ask first): **Start** on a backlog or ready ticket does what
  `safanoria-cli start` does, then opens a terminal in the new worktree; **Open terminal** on a
  ticket in progress or in review opens one where its branch is checked out; **Finish** on a
  ticket in progress sets it to `review`, as `safanoria-cli finish` does, and shows why it
  refused or what the ticket still has open. A ticket in review has **Merge and finish**, which
  does what `safanoria-cli merge` does, and **Back to in progress**, which asks for the reason
  and does what `safanoria-cli reopen` does. On Linux the terminal is
  `$TERMINAL`, else the first usual one found on the `PATH`.

The app reads the tickets again when its window gets the focus back, and on Refresh.

## Working on a ticket

```sh
safanoria-cli start herd-photos --dry-run       # what it would do
safanoria-cli start herd-photos                 # branch, status: in-progress, worktree
cd ../VacAppKMP--herd-photos && claude      # an ordinary session, in the worktree
safanoria-cli finish herd-photos                # work complete: status: review
safanoria-cli merge herd-photos                 # reviewed: merged, status: done, worktree and branch gone
safanoria-cli reopen herd-photos --reason "…"   # or: the review found something, back to in-progress
```

**Start.** `start` creates the branch `<id>` from the parent's branch (when the parent has
`childrenMergeInto: parent`, the default) or from `mainBranch`, sets `status: in-progress`, logs
`status · started` and commits that on the new branch as `<id>: start`. With `worktree` in
`safanoria.yaml` (e.g. `worktree: ../VacAppKMP--{id}`) it adds the worktree there and leaves this
checkout alone; without it, it switches this checkout to the branch (not when it has uncommitted
changes; `--no-switch` to never). It refuses tickets that aren't `backlog` or `ready`, tickets
already started (the branch exists), and children whose parent isn't started yet.

To land in the worktree, `--print-path` prints only the directory to work in (the worktree, or
this checkout once switched) and sends everything else to stderr. A program can't change its
shell's directory, so wrap it in a function in `~/.bashrc` or `~/.zshrc` (`safanoria-cli --help`
prints it too):

```sh
safanoria-start() {
  local d
  d=$(safanoria-cli start "$@" --print-path) && [ -n "$d" ] && cd "$d"
}
```

`safanoria-start herd-photos && claude` then starts the ticket and opens the session in its worktree.

**Work.** Open Claude Code in the worktree yourself. The session belongs to that worktree, so
`claude --resume` there finds it again after a restart. The SessionStart hook that `init`
installs runs `safanoria-cli context`: on a ticket's branch it gives the session the ticket and
what to do with it; elsewhere it prints nothing. From there it's an ordinary session: you
direct the work. The session keeps the ticket current. Each decision, with its reason, gets a
short Work Log entry, committed with the code it explains. It updates Objective or Acceptance
Criteria when the goal changes, and creates tickets for out-of-scope work on `main`. A child
ticket is started the same way, with `start`, in its own worktree and session.

Hook in `.claude/settings.json`, if you set it up by hand:

```json
{ "hooks": { "SessionStart": [ { "hooks": [ { "type": "command", "command": "safanoria-cli context 2>/dev/null || true" } ] } ] } }
```

**Finish.** The session runs `finish` on its own when it considers the implementation complete
(everything committed, tests passing, no question open for you), and tells you; so a ticket in
`review` is one that waits for you. You can also run it yourself. It sets `status: review`, logs
it and commits only the ticket, on the branch that has its real copy: in the worktree when the
branch is checked out there, otherwise on the branch itself, without checking it out. First it
checks that the branch is what a reviewer should see. It refuses when the worktree has
uncommitted changes, or when the branch is behind its target (commits that only touch tickets
don't count: tickets are created on `main` all the time). It lists the ticket's unchecked
Acceptance Criteria and pending Learnings without stopping for them. Then it prints what there
is to review (the diff stat against the target) and the next command.

**Review, then land or go back.** After the review, one command each way, from any checkout:

- `merge <id>` lands it. It merges the branch into its target (`main`, or the parent's branch)
  with one merge commit, `<id>: merge (done)`, that also sets `status: done`, logs it and checks
  the ticket's item in its parent's Plan: `done` and the merge are the same commit. Then it
  removes the worktree and deletes the branch (without a `worktree` setting, it switches the
  checkout back to the target). It pushes nothing, so `git reset --hard HEAD~1` on the target
  undoes it. It refuses, changing nothing, when the worktree has uncommitted changes, the merge
  conflicts (merge the target into the branch, solve it there, `merge` again), the tickets
  wouldn't validate, or the target is checked out with changes in the way. It only takes
  tickets in `review`: run `finish` first. `--dry-run` checks without merging. It needs git 2.38.
- `reopen <id> --reason "…"` sends it back to `in-progress` and logs the reason, on the same
  branch and worktree. It is for a review that sends the ticket back to work. A small change
  you ask for is committed on the branch while the ticket stays in `review`; `merge` takes the
  branch as it is then.

**Merges made elsewhere.** When the branch is merged by a pull request or by hand, the target
has the ticket in `review`. `finish --done` without an id sets every such ticket `done` (one
`<id>: done` commit each, checking the parent's Plan) and removes its worktree and local
branch; a worktree with uncommitted files is kept, with its branch. `finish <id> --done` does
one. `release` does the same before stamping, and `validate` warns about them
(`review-merged`). A squash merge leaves no trace git can follow: delete the branch, and the
ticket counts as merged.

## Releasing

```sh
safanoria-cli release app --dry-run       # which tickets would get resolvedIn.app, and the version
safanoria-cli release app                 # version from the component's source (e.g. gradle.properties)
safanoria-cli release app 4.3.0           # or given
safanoria-cli release rails 2.8.0 --ticket fix-login --ticket export-csv   # external: name what shipped
```

`release` stamps `resolvedIn.<component>` on every `done` ticket with that component in its
`area` and no version for it yet, adds a `release · <component> <version>` Work Log entry, and
sets `updated`. Each component is stamped on its own, so an app and a server in the same
repository release at their own pace; a ticket for both gets both versions. Run it on
`mainBranch` (else `--any-branch`) as part of the release commit: it doesn't commit. It
refuses a version lower than one already stamped for the component. A ticket merged into
`mainBranch` but still in `review` is set `done` first, in the same files, so it isn't left out
of the version; its worktree and branch are removed as `finish --done` does.

External components (released from another repository) are stamped here with the version
given, by hand or from that repository's release job. Use `--ticket` when `done` here doesn't
guarantee the ticket was in that release.

### Release notes

```sh
safanoria-cli notes app 4.3.0             # the tickets with resolvedIn.app 4.3.0, with their Objective
safanoria-cli notes app 4.2.0 4.3.0       # what changed after 4.2.0, up to and including 4.3.0
```

`notes` prints the tickets a version shipped, newest version first: id, type, title, parent and
Objective. That is the material, not the notes: the text users read when they update is written
from it, by an agent with the skill ("what's new in app 4.3.0?") or by hand, in their words and
with everything they don't notice reduced to one "Bug fixing" line. It reads the checkout, so
run it where the stamps are (`mainBranch` after `release`).

## Validating tickets

```sh
safanoria-cli validate                  # every ticket and safanoria.yaml
safanoria-cli validate tickets/a.md     # problems in a.md or caused by it (e.g. in its parent)
safanoria-cli validate --staged         # the git-staged tickets: for pre-commit hooks
safanoria-cli validate --format json    # {valid, checked, diagnostics: [{file, line, column, severity, code, message}]}
```

Exit code 0: valid; 1: problems; 2: usage error or not in a Safanoria repository. Problems
print as `file:line:col: error[code]: message`.

`validate` checks the files of this checkout: what you are about to commit or merge. Other
branches, local and remote-tracking, only count as known ids. A `related` to a ticket that so
far exists only on `main` (or on its own branch) isn't `ref-unknown`. `--checkout` turns that
off.

That needs the other branches to be there. Two things can leave them out:

- **A CI checkout** has one commit of one branch. The Action below fetches the tips of the
  others itself. Running the CLI yourself, do it first: `git fetch --depth=1 origin
  '+refs/heads/*:refs/remotes/origin/*'`, or `actions/checkout` with `fetch-depth: 0`.
  Without them, `validate` says so next to the `ref-unknown`.
- **A branch you haven't pushed.** `new --on main` commits to your local `main`. Push the
  ticket's branch without it and nobody else has the new ticket. `validate` warns
  (`ref-unpushed`) while a reference names a ticket that no remote-tracking branch has: push
  that branch too.

### Before each commit

```sh
safanoria-cli hook install      # once per clone: git hooks aren't committed
safanoria-cli hook uninstall
```

The hook runs `safanoria-cli validate --staged`. Where `safanoria-cli` isn't installed it warns and lets
the commit through, so a teammate without the CLI isn't blocked (CI checks anyway). A
`pre-commit` hook from another tool is never overwritten; add the line
`safanoria-cli validate --staged` to it, or to your hook manager, instead.

### In CI (GitHub Actions)

```yaml
# .github/workflows/tickets.yml
name: tickets
on: [push, pull_request]
jobs:
  validate:
    runs-on: ubuntu-24.04
    steps:
      - uses: actions/checkout@v4
      - uses: mateuy-dev/safanoria@v0.3.0      # installs the CLI, fetches the other branches, validates the tickets
```

Pin the version (`@v0.3.0`, or `with: version: 0.3.0`): a new Safanoria release then can't fail
your CI until you move to it. `with: args: …` runs another command, e.g. `list --blocked`.
Elsewhere, install with `SAFANORIA_CLI_ONLY=1` and `install.sh`, and run `safanoria-cli validate`.

### Problem codes

| Code | Problem (SPEC section) |
|---|---|
| `yaml-syntax`, `frontmatter-missing`, `config-missing` | The file can't be read (§2, §4) |
| `schema-<keyword>` | A frontmatter or config rule of `schema/` (§2, §5): `schema-required`, `schema-enum`, `schema-pattern`… |
| `id-mismatch`, `id-duplicate` | id differs from the filename, or is used twice (§3) |
| `area-required`, `area-unknown-component` | `area` missing with several components, or not a component (§5) |
| `tag-unknown` | a tag that `safanoria.yaml` `tags` doesn't declare (§2, §5) |
| `resolved-in-not-allowed`, `resolved-in-unknown-component`, `resolved-in-not-in-area` | `resolvedIn` on a ticket that isn't `done`, on `research`/`wontfix`, or for a wrong component (§9) |
| `channel-unknown`, `requests-quotes-mismatch` | request channel not configured; requests and quotes differ in count (§5, §7.3) |
| `quote-attribution`, `work-log-entry`, `learning-resolution` | Malformed quote attribution, Work Log entry or Learning `→` line (§7.3, §7.7, §7.6) |
| `section-missing`, `section-order`, `section-duplicate`, `section-empty` | Sections (§7, §7.1); extra sections may go anywhere |
| `ref-unknown` | `parent`, `blockedBy`, `related`, a Plan child item or a Learning names no ticket (§8) |
| `ref-unpushed` (warning) | A reference to a ticket that is only on a local branch no remote has: push that branch too, or CI won't know it (§14.2) |
| `blocked-by-cycle` | `blockedBy` cycle (§8.2) |
| `parent-nested`, `research-parent` | Two levels of parents; research ticket with children (§8.1, §6.2) |
| `parent-plan-missing-child`, `parent-plan-duplicate-child`, `plan-item-not-child`, `child-check-mismatch` | Parent Plan and children out of sync (§7.5, §8.1) |
| `child-resolved-later` | A child released after its parent (§9) |
| `attachment-missing` | A link to a file under `attachments/` that isn't there (§7.8) |
| `attachment-large` (warning) | An attachment over 1 MB (§7.8); warnings don't change the exit code |
| `id-created-twice` (warning) | Another branch created a ticket with this id separately; they will conflict at merge, so rename one (§14.3) |
| `review-merged` (warning) | A ticket merged into its target but still `review`: `finish --done` sets it `done` (§6.1) |

## Planned

- The desktop app (`gui/`): editing tickets, marking them done.

## Development

The CLI is Kotlin Multiplatform: `core/` holds all logic (config, parser, schema checks,
targeted edits, git) and is shared with the apps; `cli/` is the `safanoria-cli` command, built
as a native binary for Linux (x64), Windows (x64) and macOS (arm64); `gui/` is the desktop app,
the `safanoria` command (Compose Desktop, JVM only; installed with its own Java runtime, as
`./gradlew :gui:createDistributable` builds it).

```sh
make install                                        # build for this OS; safanoria-cli and safanoria in ~/.local/bin (PREFIX=… to change)
make install-cli                                    # only the CLI, which is much faster to build
safanoria-cli hook install                              # validate this repository's tickets before each commit
./gradlew allTests                                  # JVM tests + native tests for this OS
./gradlew :gui:run --args="$PWD"                    # the desktop app, on this repository
./gradlew :cli:linkReleaseExecutableLinuxX64        # or …MingwX64, …MacosArm64 (on that OS)
cli/build/bin/linuxX64/releaseExecutable/safanoria.kexe version
python3 tools/bench.py cli/build/bin/linuxX64/releaseExecutable/safanoria.kexe   # startup times
```

- The hook runs the installed `safanoria-cli`, not this checkout's. After CLI changes are merged,
  `make install-cli` again: an older CLI can report errors the new one wouldn't (e.g. a related
  ticket that exists only on `main`, before cross-branch ids).
- Needs JDK 21. The first build downloads the Kotlin/Native toolchain into `~/.konan`.
- Linux binaries link the system `libunistring.so.5` (Ubuntu 24.04+, `libunistring5`), needed by
  the JSON Schema validator; they are built on Linux only.
- Tests also read other local repositories' tickets when `SAFANORIA_EXTRA_REPOS` lists their
  roots (`:`-separated). Those tickets are only read, never copied here.
- `schema/*.json`, `templates/`, `skill/SKILL.md` and `SPEC.md` are embedded into `core` at
  build time (edit them, not the generated code), so `init` and `update` install exactly this
  checkout's copies.
- Versions (SPEC §13): `gradle.properties` `version` is the Safanoria version being developed,
  plain `MAJOR.MINOR.PATCH`; it is also this repository's `safanoria` component version. Builds
  report it with `-dev`; release builds (`-Prelease`, from the `vX.Y.Z` tag) report it as is.
  The spec version (`safanoria: 1`) changes only on breaking spec changes.

### Releasing Safanoria

`./release.sh` releases the `version` in `gradle.properties`, from `main` with nothing
uncommitted. It shows what it will do and asks first (`-y` to skip), then:

1. Runs the tests (`./gradlew allTests`), and stops there if they fail. Then stamps this
   repository's tickets (`safanoria-cli release safanoria`), moves the version
   pinned in this README's Action example, and commits `Release Safanoria X.Y.Z`.
2. Tags it `vX.Y.Z`. The `release` workflow checks the
   tag equals `version`, builds the CLI binaries and the app archives for the three systems
   with `-Prelease`, and publishes them with `SHA256SUMS` as a GitHub release, which the install
   scripts download. The CLI assets are named `safanoria-<os>` without "cli": install scripts
   and Actions pinned to a version from before the app download the latest release by those names.
3. Sets `version` to the next one for development (the next minor, or `./release.sh 1.0.0`),
   commits `Start X.Y.Z`, and pushes `main` and the tag.

Changing `.github/workflows/release.yml`, the install scripts or `action.yml` on any branch runs
the workflow without publishing: it builds the binaries and the app, and runs both install scripts and the
Action against them.

## License

[MIT](LICENSE)

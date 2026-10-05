# Safanoria

Tickets as markdown files in the project's own repository: the backlog and the progress of
every feature, tracked in git next to the code.

The way of working is deliberately plain. You ask an agent session to create tickets. To work
on one, you start it with the CLI (a branch and a worktree) and open an ordinary Claude Code
session in that worktree. The session knows its ticket from the branch, works as you direct it,
and records its decisions in the ticket as it commits. No planning or approval steps.

- [`SPEC.md`](SPEC.md): the format, version 1. The contract for every tool.
- [`skill/`](skill/): agent workflow (Claude Code skill). `safanoria init` / `update` install it,
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
`safanoria validate` for tickets (below).

The `safanoria` CLI does the mechanical parts, so agents and people spend their time on the
content. `safanoria --help` lists the commands; each is described below.

| Command | Does |
|---|---|
| `init`, `update` | Set up a project; install or update the skill, SPEC.md and templates |
| `new` | Create a ticket from a title: id, template, parent's Plan |
| `list`, `board` | One line per ticket; a markdown board by status |
| `start`, `finish` | Start a ticket (branch, worktree, `in-progress`); set it `review`, or `done` once merged |
| `context` | The current branch's ticket, for the agent session (SessionStart hook) |
| `validate`, `hook` | Check every SPEC rule; before each commit |
| `release` | Stamp `resolvedIn` on the tickets a release ships |
| `notes` | The tickets a version shipped, to write its release notes from |

## Installing the CLI

```sh
curl -fsSL https://raw.githubusercontent.com/mateuy-dev/safanoria/main/install.sh | sh      # Linux x64, macOS arm64
irm https://raw.githubusercontent.com/mateuy-dev/safanoria/main/install.ps1 | iex           # Windows x64 (PowerShell)
```

Releases start at `v0.1.0`; until it is published, use `make install` from a checkout.
The scripts download the latest release (`SAFANORIA_VERSION=0.1.0` for another), check its
checksum, and put `safanoria` in `~/.local/bin` (`SAFANORIA_BIN_DIR` to change it). The Linux
binary needs `libunistring.so.5` (Ubuntu 24.04+, Debian 13+: package `libunistring5`). From a
checkout: `make install`.

## Adding Safanoria to a project

```sh
safanoria init                      # asks for the components (parts released with their own version)
safanoria init --component app=composeApp/gradle.properties:appVersionName --external rails
safanoria update                    # later: this version's skill and spec, and any new templates
```

`init` writes `safanoria.yaml`, the ticket directory (`_TEMPLATE.md`, `_TEMPLATE.bug.md`,
`_TEMPLATE.research.md`, `README.md`), the skill and SPEC.md in `.claude/skills/safanoria/`,
the paragraph that points agents to the skill in `CLAUDE.md`, and the SessionStart hook in
`.claude/settings.json` (see "Working on a ticket"). The skill and spec are Safanoria's and end
with `<!-- safanoria X.Y.Z -->`; `update` replaces them and says which version was there before.
Templates, the ticket README, `CLAUDE.md` and `.claude/settings.json` are the project's: they
are created when missing and changed only after asking (`--yes` to accept, `--dry-run` to see).

Then make the release process run `safanoria release <component>` (see "Releasing"; SPEC §9).

## Creating tickets

Usually you ask the agent ("create a ticket for…", "log this user request"), in any session:
the main checkout for backlog work, or a ticket's worktree when the work turns up there. It
writes the Objective from the conversation, picks the id and tells you, and runs `new` for the
rest. Ask it to rename the id if you don't like it: before the ticket is started that's cheap.
The command, which you can use too:

```sh
safanoria new "Herd photos from the field" --dry-run   # suggested id, files it would write
safanoria new "Herd photos from the field" --id herd-photos --size M --objective "Why…"
safanoria new "Map pin" --parent herd-locations          # child: herd-locations-map-pin, added to the parent's Plan
safanoria new "Export is slow" --on main                 # from another branch: committed on main, this checkout untouched
```

`new` fills the template for the type (`<dir>/_TEMPLATE.<type>.md`, else `<dir>/_TEMPLATE.md`,
else the built-in one, which for bugs has Steps to reproduce, Expected and Actual; `--objective`
keeps those subsections) with the id, title, type, priority, size, `area` (required when there
are several components), `status: backlog` and today's date. It refuses an id that exists on
any branch (ids are never reused), an unknown parent, and a parent that can't have children; a
branch with the same name is a warning. The suggested id is short (filler words dropped, at
most four words); it's the branch name.

**On a terminal, the CLI asks** for what you leave out. `safanoria` alone asks which command to
run. `new` without a title asks for the title, the type and the id (enter keeps the suggestion),
optionally priority, size, parent and objective, and, on a branch other than `mainBranch`,
whether the ticket goes on `mainBranch`; with a title it asks only for a missing `area`.
`start`, `finish` and `release` without an id or component offer a list (backlog and ready
tickets to start; in-progress ones to finish, or in review with `--done`). `init` asks for the
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
safanoria list                                  # one line per ticket, in-progress first
safanoria list --status ready,backlog --type bug
safanoria list --parent v1-tooling --blocked    # children blocked by a ticket that is not done
safanoria list --format json                    # {tickets: [...]}: every field, plus children, blocks, openBlockers, progress
safanoria board                                 # markdown board on stdout
safanoria board -o docs/BOARD.md                # links relative to the file
safanoria list --remote                         # also origin/* branches (git fetch first)
safanoria list --checkout                       # only this checkout's files
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
the parent's `done/total` Plan items (children and own steps). The board has no dates, so a
committed one only changes when tickets do. For example:

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

A desktop app shows the same tickets as a board you can click through. It runs on the JVM
(Java 21) from a checkout of this repository; there is no installer yet:

```sh
./gradlew :gui:run --args=/path/to/project   # a directory in the project; default: where gradle runs
```

- **Board**: a column per status, left to right as a ticket moves (Backlog, Ready, In progress,
  Review, Done, Won't fix). Every ticket is a card, children too, read from every local branch
  as `list` does. Done and Won't fix start collapsed: click a column's header to collapse or
  open it. Chips filter by type, area and blocked. A card shows its parent, a parent's
  `done/total` Plan items, open blockers, and how many problems `validate` finds in it.
- **Ticket**: the body as rendered markdown, its fields, links to its parent, children and
  blockers, and its `validate` problems. Problems are those of this checkout's files, so a
  ticket shown from another branch shows none.
- **Actions**, in the bar under the ticket (Start and Finish ask first): **Start** on a backlog or ready ticket does what
  `safanoria start` does, then opens a terminal in the new worktree; **Open terminal** on a
  ticket in progress or in review opens one where its branch is checked out; **Finish** on a
  ticket in progress sets it to `review`, as `safanoria finish` does. On Linux the terminal is
  `$TERMINAL`, else the first usual one found on the `PATH`.

The app reads the tickets again when its window gets the focus back, and on Refresh.

## Working on a ticket

```sh
safanoria start herd-photos --dry-run       # what it would do
safanoria start herd-photos                 # branch, status: in-progress, worktree
cd ../VacAppKMP--herd-photos && claude      # an ordinary session, in the worktree
safanoria finish herd-photos                # work complete: status: review
safanoria finish herd-photos --done         # after the merge: status: done
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
shell's directory, so wrap it in a function in `~/.bashrc` or `~/.zshrc` (`safanoria --help`
prints it too):

```sh
safanoria-start() {
  local d
  d=$(safanoria start "$@" --print-path) && [ -n "$d" ] && cd "$d"
}
```

`safanoria-start herd-photos && claude` then starts the ticket and opens the session in its worktree.

**Work.** Open Claude Code in the worktree yourself. The session belongs to that worktree, so
`claude --resume` there finds it again after a restart. The SessionStart hook that `init`
installs runs `safanoria context`: on a ticket's branch it gives the session the ticket and
what to do with it; elsewhere it prints nothing. From there it's an ordinary session: you
direct the work. The session keeps the ticket current. Each decision, with its reason, gets a
short Work Log entry, committed with the code it explains. It updates Objective or Acceptance
Criteria when the goal changes, and creates tickets for out-of-scope work on `main`. A child
ticket is started the same way, with `start`, in its own worktree and session.

Hook in `.claude/settings.json`, if you set it up by hand:

```json
{ "hooks": { "SessionStart": [ { "hooks": [ { "type": "command", "command": "safanoria context 2>/dev/null || true" } ] } ] } }
```

**Finish.** Tell the session the work is done, or run `finish`. It sets `status: review` and
logs it. `finish --done` sets `done` once the branch is merged into its target (it refuses
before), and checks the ticket's item in its parent's Plan. Either way, it commits only the
ticket, on the branch that has its real copy. That's the worktree when the branch is checked
out there, otherwise the branch itself, without checking it out.

## Releasing

```sh
safanoria release app --dry-run       # which tickets would get resolvedIn.app, and the version
safanoria release app                 # version from the component's source (e.g. gradle.properties)
safanoria release app 4.3.0           # or given
safanoria release rails 2.8.0 --ticket fix-login --ticket export-csv   # external: name what shipped
```

`release` stamps `resolvedIn.<component>` on every `done` ticket with that component in its
`area` and no version for it yet, adds a `release · <component> <version>` Work Log entry, and
sets `updated`. Each component is stamped on its own, so an app and a server in the same
repository release at their own pace; a ticket for both gets both versions. Run it on
`mainBranch` (else `--any-branch`) as part of the release commit: it doesn't commit. It
refuses a version lower than one already stamped for the component.

External components (released from another repository) are stamped here with the version
given, by hand or from that repository's release job. Use `--ticket` when `done` here doesn't
guarantee the ticket was in that release.

### Release notes

```sh
safanoria notes app 4.3.0             # the tickets with resolvedIn.app 4.3.0, with their Objective
safanoria notes app 4.2.0 4.3.0       # what changed after 4.2.0, up to and including 4.3.0
```

`notes` prints the tickets a version shipped, newest version first: id, type, title, parent and
Objective. That is the material, not the notes: the text users read when they update is written
from it, by an agent with the skill ("what's new in app 4.3.0?") or by hand, in their words and
with everything they don't notice reduced to one "Bug fixing" line. It reads the checkout, so
run it where the stamps are (`mainBranch` after `release`).

## Validating tickets

```sh
safanoria validate                  # every ticket and safanoria.yaml
safanoria validate tickets/a.md     # problems in a.md or caused by it (e.g. in its parent)
safanoria validate --staged         # the git-staged tickets: for pre-commit hooks
safanoria validate --format json    # {valid, checked, diagnostics: [{file, line, column, severity, code, message}]}
```

Exit code 0: valid; 1: problems; 2: usage error or not in a Safanoria repository. Problems
print as `file:line:col: error[code]: message`.

`validate` checks the files of this checkout: what you are about to commit or merge. Other
branches, local and remote-tracking, only count as known ids. A `related` to a ticket that so
far exists only on `main` (or on its own branch) isn't `ref-unknown`. `--checkout` turns that
off. In CI, `actions/checkout` with `fetch-depth: 0` gives it every branch.

### Before each commit

```sh
safanoria hook install      # once per clone: git hooks aren't committed
safanoria hook uninstall
```

The hook runs `safanoria validate --staged`. Where `safanoria` isn't installed it warns and lets
the commit through, so a teammate without the CLI isn't blocked (CI checks anyway). A
`pre-commit` hook from another tool is never overwritten; add the line
`safanoria validate --staged` to it, or to your hook manager, instead.

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
      - uses: mateuy-dev/safanoria@v0.1.0      # installs the CLI, runs `safanoria validate`
```

Pin the version (`@v0.1.0`, or `with: version: 0.1.0`): a new Safanoria release then can't fail
your CI until you move to it. `with: args: …` runs another command, e.g. `list --blocked`.
Elsewhere, install with `install.sh` and run `safanoria validate`.

### Problem codes

| Code | Problem (SPEC section) |
|---|---|
| `yaml-syntax`, `frontmatter-missing`, `config-missing` | The file can't be read (§2, §4) |
| `schema-<keyword>` | A frontmatter or config rule of `schema/` (§2, §5): `schema-required`, `schema-enum`, `schema-pattern`… |
| `id-mismatch`, `id-duplicate` | id differs from the filename, or is used twice (§3) |
| `area-required`, `area-unknown-component` | `area` missing with several components, or not a component (§5) |
| `resolved-in-not-allowed`, `resolved-in-unknown-component`, `resolved-in-not-in-area` | `resolvedIn` on a ticket that isn't `done`, on `research`/`wontfix`, or for a wrong component (§9) |
| `channel-unknown`, `requests-quotes-mismatch` | request channel not configured; requests and quotes differ in count (§5, §7.3) |
| `quote-attribution`, `work-log-entry`, `learning-resolution` | Malformed quote attribution, Work Log entry or Learning `→` line (§7.3, §7.7, §7.6) |
| `section-missing`, `section-order`, `section-duplicate`, `section-empty` | Sections (§7, §7.1); extra sections may go anywhere |
| `ref-unknown` | `parent`, `blockedBy`, `related`, a Plan child item or a Learning names no ticket (§8) |
| `blocked-by-cycle` | `blockedBy` cycle (§8.2) |
| `parent-nested`, `research-parent` | Two levels of parents; research ticket with children (§8.1, §6.2) |
| `parent-plan-missing-child`, `parent-plan-duplicate-child`, `plan-item-not-child`, `child-check-mismatch` | Parent Plan and children out of sync (§7.5, §8.1) |
| `child-resolved-later` | A child released after its parent (§9) |
| `attachment-missing` | A link to a file under `attachments/` that isn't there (§7.8) |
| `attachment-large` (warning) | An attachment over 1 MB (§7.8); warnings don't change the exit code |
| `id-created-twice` (warning) | Another branch created a ticket with this id separately; they will conflict at merge, so rename one (§14.3) |

## Planned

- The desktop app (`gui/`): an installer, editing tickets, marking them done.

## Development

The CLI is Kotlin Multiplatform: `core/` holds all logic (config, parser, schema checks,
targeted edits, git) and is shared with the apps; `cli/` is the `safanoria` command, built
as a native binary for Linux (x64), Windows (x64) and macOS (arm64); `gui/` is the desktop app
(Compose Desktop, JVM only).

```sh
make install                                        # build for this OS, copy to ~/.local/bin/safanoria (PREFIX=… to change)
safanoria hook install                              # validate this repository's tickets before each commit
./gradlew allTests                                  # JVM tests + native tests for this OS
./gradlew :gui:run --args="$PWD"                    # the desktop app, on this repository
./gradlew :cli:linkReleaseExecutableLinuxX64        # or …MingwX64, …MacosArm64 (on that OS)
cli/build/bin/linuxX64/releaseExecutable/safanoria.kexe version
python3 tools/bench.py cli/build/bin/linuxX64/releaseExecutable/safanoria.kexe   # startup times
```

- The hook runs the installed `safanoria`, not this checkout's. After CLI changes are merged,
  `make install` again: an older CLI can report errors the new one wouldn't (e.g. a related
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

1. On `main`, with `gradle.properties` `version` set to the release (e.g. `0.2.0`): stamp this
   repository's tickets, `safanoria release safanoria`, and commit.
2. Tag and push: `git tag v0.2.0 && git push origin v0.2.0`. The `release` workflow checks the
   tag equals `version`, builds the three binaries with `-Prelease`, and publishes them with
   `SHA256SUMS` as a GitHub release, which the install scripts download.
3. Set `version` to the next one (e.g. `0.3.0`) for development.

Changing `.github/workflows/release.yml`, the install scripts or `action.yml` on any branch runs
the workflow without publishing: it builds the binaries and runs both install scripts and the
Action against them.

## License

[MIT](LICENSE)

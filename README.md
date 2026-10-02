# Safanoria

Tickets as markdown files in the project's own repository, written to be the working context
for humans and AI agents.

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
| `validate`, `hook` | Check every SPEC rule; before each commit |
| `release` | Stamp `resolvedIn` on the tickets a release ships |

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
and the paragraph that points agents to the skill in `CLAUDE.md`. The skill and spec are
Safanoria's and end with `<!-- safanoria X.Y.Z -->`; `update` replaces them and says which
version was there before. Templates, the ticket README and `CLAUDE.md` are the project's: they
are created when missing and changed only after asking (`--yes` to accept, `--dry-run` to see).

Then make the release process run `safanoria release <component>` (see "Releasing"; SPEC §9).

## Creating tickets

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
most four words): confirm or change it, it's the branch name.

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

**Every branch.** A ticket you start moves to its own branch: there it becomes `in-progress`,
gets a Plan and a Work Log, while `main` keeps the `backlog` copy until the merge. So `list` and
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
| `plan-unchecked`, `learning-pending` | Unfinished work at `review`/`done` (§7.1, §7.6) |
| `ref-unknown` | `parent`, `blockedBy`, `related`, a Plan child item or a Learning names no ticket (§8) |
| `blocked-by-cycle` | `blockedBy` cycle (§8.2) |
| `parent-nested`, `research-parent` | Two levels of parents; research ticket with children (§8.1, §6.2) |
| `parent-plan-missing-child`, `parent-plan-duplicate-child`, `plan-item-not-child`, `child-check-mismatch` | Parent Plan and children out of sync (§7.5, §8.1) |
| `child-resolved-later` | A child released after its parent (§9) |
| `attachment-missing` | A link to a file under `attachments/` that isn't there (§7.8) |
| `attachment-large` (warning) | An attachment over 1 MB (§7.8); warnings don't change the exit code |
| `id-created-twice` (warning) | Another branch created a ticket with this id separately; they will conflict at merge, so rename one (§14.3) |

## Planned

- `apps/`: viewers and editors.

## Development

The CLI is Kotlin Multiplatform: `core/` holds all logic (config, parser, schema checks,
targeted edits, git) and is shared with future apps; `cli/` is the `safanoria` command, built
as a native binary for Linux (x64), Windows (x64) and macOS (arm64).

```sh
make install                                        # build for this OS, copy to ~/.local/bin/safanoria (PREFIX=… to change)
safanoria hook install                              # validate this repository's tickets before each commit
./gradlew allTests                                  # JVM tests + native tests for this OS
./gradlew :cli:linkReleaseExecutableLinuxX64        # or …MingwX64, …MacosArm64 (on that OS)
cli/build/bin/linuxX64/releaseExecutable/safanoria.kexe version
python3 tools/bench.py cli/build/bin/linuxX64/releaseExecutable/safanoria.kexe   # startup times
```

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

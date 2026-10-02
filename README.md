# Safanoria

Tickets as markdown files in the project's own repository, written to be the working context
for humans and AI agents.

- [`SPEC.md`](SPEC.md): the format, version 1. The contract for every tool.
- [`skill/`](skill/): agent workflow (Claude Code skill). Install by copying `skill/SKILL.md`
  and `SPEC.md` into the project's `.claude/skills/safanoria/`.
- [`templates/ticket.md`](templates/ticket.md): blank ticket; copy to `<dir>/_TEMPLATE.md`.
- [`schema/`](schema/): JSON Schemas for ticket frontmatter and `safanoria.yaml`, with valid and
  invalid examples (checked by `core`'s tests). The CLI embeds them.

For completion and checks in editors with the YAML language server (VS Code YAML extension,
IntelliJ), start `safanoria.yaml` with:

```yaml
# yaml-language-server: $schema=https://raw.githubusercontent.com/mateuy-dev/safanoria/main/schema/safanoria.schema.json
```

Ticket frontmatter is inside markdown, which editors don't check against a schema; use
`safanoria validate` for tickets (below).

## Adding Safanoria to a project

1. Create `safanoria.yaml` at the repository root (SPEC §2).
2. Create the ticket directory with `_TEMPLATE.md` and a short `README.md` pointing here.
3. Install the skill (above).
4. Add to the project's `CLAUDE.md`:
   > Work is tracked as Safanoria tickets in `tickets/<id>.md`. The ticket id is also the branch
   > name. When creating, planning or working on a ticket, use the `safanoria` skill.
5. Make the release process stamp `resolvedIn` (SPEC §9).

## Creating tickets

```sh
safanoria new "Herd photos from the field" --dry-run   # suggested id, files it would write
safanoria new "Herd photos from the field" --id herd-photos --size M --objective "Why…"
safanoria new "Map pin" --parent herd-locations          # child: herd-locations-map-pin, added to the parent's Plan
```

`new` fills `<dir>/_TEMPLATE.md` (or the built-in template) with the id, title, type,
priority, size, `area` (required when there are several components), `status: backlog` and
today's date. It refuses an id that exists (ids are never reused), an unknown parent, and a
parent that can't have children; a branch with the same name is a warning. The suggested id is
short (filler words dropped, at most four words): confirm or change it, it's the branch name.

## Viewing tickets

```sh
safanoria list                                  # one line per ticket, in-progress first
safanoria list --status ready,backlog --type bug
safanoria list --parent v1-tooling --blocked    # children blocked by a ticket that is not done
safanoria list --format json                    # {tickets: [...]}: every field, plus children, blocks, openBlockers, progress
safanoria board                                 # markdown board on stdout
safanoria board -o docs/BOARD.md                # links relative to the file
```

`list` and `board` show tickets in the same order: status (in-progress, review, ready, backlog,
done, wontfix), then priority, then id. In `board`, children appear under their parent with
the parent's `done/total` Plan items (children and own steps). The board has no dates, so a
committed one only changes when tickets do. This repository's board, shortened:

```markdown
## In progress (1)

- [v1-tooling](tickets/v1-tooling.md) Tooling and open points to make Safanoria v1 usable across projects (high) · 8/14
  - [x] [v1-tooling-new](tickets/v1-tooling-new.md) `safanoria new`: create a ticket from a title
  - [ ] [v1-tooling-board](tickets/v1-tooling-board.md) `safanoria list` and `safanoria board` (in-progress)
  - [ ] [v1-tooling-install](tickets/v1-tooling-install.md) Install the CLI, and set up or update Safanoria in a project with one command (backlog, high) · blocked by [v1-tooling-spec-decisions](tickets/v1-tooling-spec-decisions.md)

## Backlog (1)

- [gui-viewer](tickets/gui-viewer.md) Compose Desktop app to view tickets
```

## Validating tickets

```sh
safanoria validate                  # every ticket and safanoria.yaml
safanoria validate tickets/a.md     # problems in a.md or caused by it (e.g. in its parent)
safanoria validate --staged         # the git-staged tickets: for pre-commit hooks
safanoria validate --format json    # {valid, checked, diagnostics: [{file, line, column, severity, code, message}]}
```

Exit code 0: valid; 1: problems; 2: usage error or not in a Safanoria repository. Problems
print as `file:line:col: error[code]: message`. Until the hooks ticket ships an installer, a
pre-commit hook is one line in `.git/hooks/pre-commit` (made executable):

```sh
#!/bin/sh
exec safanoria validate --staged
```

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

## Planned

- CLI commands: `release <component> <version>`, `init`.
- `apps/`: viewers and editors.

## Development

The CLI is Kotlin Multiplatform: `core/` holds all logic (config, parser, schema checks,
targeted edits, git) and is shared with future apps; `cli/` is the `safanoria` command, built
as a native binary for Linux (x64), Windows (x64) and macOS (arm64).

```sh
make install                                        # build for this OS, copy to ~/.local/bin/safanoria (PREFIX=… to change)
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
- `schema/*.json` are embedded into `core` at build time; edit the schemas, not the generated code.
- Versions (SPEC §13): `gradle.properties` `version` is the Safanoria version being developed,
  plain `MAJOR.MINOR.PATCH`; it is also this repository's `safanoria` component version. Builds
  report it with `-dev`; release builds (`-Prelease`, from the `vX.Y.Z` tag) report it as is.
  The spec version (`safanoria: 1`) changes only on breaking spec changes.

## License

[MIT](LICENSE)

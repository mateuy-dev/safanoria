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

- CLI commands: `new`, `list`, `board`, `release <component> <version>`, `init`.
- `apps/`: viewers and editors.

## Development

The CLI is Kotlin Multiplatform: `core/` holds all logic (config, parser, schema checks,
targeted edits, git) and is shared with future apps; `cli/` is the `safanoria` command, built
as a native binary for Linux (x64), Windows (x64) and macOS (arm64).

```sh
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

## License

[MIT](LICENSE)

# Safanoria

Tickets as markdown files in the project's own repository, written to be the working context
for humans and AI agents.

- [`SPEC.md`](SPEC.md): the format, version 1. The contract for every tool.
- [`skill/`](skill/): agent workflow (Claude Code skill). Install by copying `skill/SKILL.md`
  and `SPEC.md` into the project's `.claude/skills/safanoria/`.
- [`templates/ticket.md`](templates/ticket.md): blank ticket; copy to `<dir>/_TEMPLATE.md`.
- [`schema/`](schema/): JSON Schemas for ticket frontmatter and `safanoria.yaml`, with valid and
  invalid examples. `python3 schema/check.py` checks them (needs PyYAML and `jsonschema`).

For completion and checks in editors with the YAML language server (VS Code YAML extension,
IntelliJ), start `safanoria.yaml` with:

```yaml
# yaml-language-server: $schema=https://raw.githubusercontent.com/mateuy-dev/safanoria/main/schema/safanoria.schema.json
```

Ticket frontmatter is inside markdown, which editors don't check against a schema; use
`safanoria validate` (planned) for tickets.

## Adding Safanoria to a project

1. Create `safanoria.yaml` at the repository root (SPEC §2).
2. Create the ticket directory with `_TEMPLATE.md` and a short `README.md` pointing here.
3. Install the skill (above).
4. Add to the project's `CLAUDE.md`:
   > Work is tracked as Safanoria tickets in `tickets/<id>.md`. The ticket id is also the branch
   > name. When creating, planning or working on a ticket, use the `safanoria` skill.
5. Make the release process stamp `resolvedIn` (SPEC §9).

## Planned

- CLI commands: `new`, `validate`, `list`, `board`, `release <component> <version>`, `init`.
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

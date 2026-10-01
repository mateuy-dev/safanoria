# Safanoria

Tickets as markdown files in the project's own repository, written to be the working context
for humans and AI agents.

- [`SPEC.md`](SPEC.md): the format, version 1. The contract for every tool.
- [`skill/`](skill/): agent workflow (Claude Code skill). Install by copying `skill/SKILL.md`
  and `SPEC.md` into the project's `.claude/skills/safanoria/`.
- [`templates/ticket.md`](templates/ticket.md): blank ticket; copy to `<dir>/_TEMPLATE.md`.

## Adding Safanoria to a project

1. Create `safanoria.yaml` at the repository root (SPEC §2).
2. Create the ticket directory with `_TEMPLATE.md` and a short `README.md` pointing here.
3. Install the skill (above).
4. Add to the project's `CLAUDE.md`:
   > Work is tracked as Safanoria tickets in `tickets/<id>.md`. The ticket id is also the branch
   > name. When creating, planning or working on a ticket, use the `safanoria` skill.
5. Make the release process stamp `resolvedIn` (SPEC §9).

## Planned

- `schema/`: JSON Schema for the frontmatter and `safanoria.yaml`.
- `cli/`: `new`, `validate`, `board`, `release <component> <version>`.
- `apps/`: viewers and editors.

## License

[MIT](LICENSE)

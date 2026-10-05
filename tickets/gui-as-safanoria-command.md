---
id: gui-as-safanoria-command
type: feature
title: "safanoria opens the desktop app; the CLI becomes safanoria-cli"
status: done
priority: medium
size: M
created: 2026-10-05
updated: 2026-10-05
resolvedIn:
  safanoria: 0.2.0
---

## Objective

Typing `safanoria` should open the desktop app (`gui/`) on the project of the current directory. The command-line tool, which has that name today, is renamed to `safanoria-cli`.

Today the app has no installed command: it only runs from a checkout with `./gradlew :gui:run --args=<path>`, and `safanoria` is the native CLI binary (`cli/build.gradle.kts` `baseName`, `Makefile`, `install.sh`, `install.ps1`). So this needs both a way to install the app as a command (it is JVM-only Compose Desktop, the CLI is a native binary) and the rename.

The rename reaches everything that calls or names the CLI: the build and install scripts, `action.yml`, the hooks and `.claude/settings.json`, the skill (`skill/SKILL.md`), `SPEC.md`, `README.md`, the templates, and the CLI's own help and messages. Projects that already have `safanoria` installed, and their hooks and CI, keep calling the old name: decide what happens to them on update (the installer removing or replacing the old binary, a note in the release notes).

## Acceptance Criteria

- [x] After installing (`install.sh`, `install.ps1`, `make install`), `safanoria` opens the app on the project of the working directory, and `safanoria <dir>` on another.
- [x] The command-line tool is `safanoria-cli` everywhere it is called or named: binary, help and messages, hooks, Action, skill, SPEC, README.
- [x] Installing over 0.2 or earlier leaves no old `safanoria` CLI binary behind.
- [x] A hook or script that still calls `safanoria <command>` opens no window and is told the new name; `safanoria-cli update` and `safanoria-cli hook install` rename the project's hooks.

## Work Log

- **2026-10-05** · status · started
- **2026-10-05** · decision · The app is installed as a jpackage app image with its own Java runtime (`:gui:createDistributable`, about 150 MB, one archive per system in the release), plus a small `safanoria` launcher on the PATH. Rejected a jar that needs an installed Java: people who install a native CLI don't have one.
- **2026-10-05** · decision · The release's CLI assets keep their names (`safanoria-<os>`, no "cli"). Actions and install scripts pinned to an older tag download the latest release by those names, so their CI keeps working; the app's are `safanoria-app-<os>`.
- **2026-10-05** · decision · Old callers: the installer replaces the old `safanoria` binary (with `SAFANORIA_CLI_ONLY=1`, which the Action uses, it removes it). `safanoria` with anything that isn't one directory prints "use `safanoria-cli <same args>`" and exits 2, so an old git hook refuses the commit with that message instead of opening a window. No forwarding to the CLI: it would keep the old name alive. `update` renames the SessionStart hook and `hook install` rewrites an outdated git hook.
- **2026-10-05** · decision · `safanoria` runs in the foreground, so its messages (no project here) show in the terminal; on Windows the app is built with a console for the same reason. Detaching is left to the shell (`safanoria &`).
- **2026-10-05** · decision · Not tried on a machine: the macOS and Windows packages and `install.ps1` (only Linux here). The `release` workflow's install test covers them when this branch is pushed.
- **2026-10-05** · status · review
- **2026-10-05** · status · done
- **2026-10-05** · release · safanoria 0.2.0

---
id: gui-as-safanoria-command
type: feature
title: "safanoria opens the desktop app; the CLI becomes safanoria-cli"
status: in-progress
priority: medium
size: M
created: 2026-10-05
updated: 2026-10-05
---

## Objective

Typing `safanoria` should open the desktop app (`gui/`) on the project of the current directory. The command-line tool, which has that name today, is renamed to `safanoria-cli`.

Today the app has no installed command: it only runs from a checkout with `./gradlew :gui:run --args=<path>`, and `safanoria` is the native CLI binary (`cli/build.gradle.kts` `baseName`, `Makefile`, `install.sh`, `install.ps1`). So this needs both a way to install the app as a command (it is JVM-only Compose Desktop, the CLI is a native binary) and the rename.

The rename reaches everything that calls or names the CLI: the build and install scripts, `action.yml`, the hooks and `.claude/settings.json`, the skill (`skill/SKILL.md`), `SPEC.md`, `README.md`, the templates, and the CLI's own help and messages. Projects that already have `safanoria` installed, and their hooks and CI, keep calling the old name: decide what happens to them on update (the installer removing or replacing the old binary, a note in the release notes).

## Acceptance Criteria

## Work Log

- **2026-10-05** · status · started

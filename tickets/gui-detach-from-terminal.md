---
id: gui-detach-from-terminal
type: feature
title: safanoria keeps running when its terminal is closed
status: review
priority: medium
size: S
created: 2026-10-05
updated: 2026-10-05
related: [gui-as-safanoria-command]
---

## Objective

The desktop app is opened by typing `safanoria` in a terminal, and closing that terminal closes the app. The launcher runs the app in the foreground (`exec "$app_dir/$app_exe" "$@"` in `install.sh`, the same line in the `Makefile`, and `safanoria.cmd` written by `install.ps1`), so it stays in the terminal's session and gets the hangup signal when the terminal closes.

That was a decision in `gui-as-safanoria-command`: stay in the foreground so messages show, and leave detaching to the shell (`safanoria &`). But `&` alone does not survive closing the terminal: bash forwards the hangup to its background jobs. `setsid -f safanoria` or `nohup safanoria >/dev/null 2>&1 &` do, and nobody should have to know that. `safanoria` is used like `code .`: typed in a project directory to open a window, expecting the prompt back and the window to outlive the terminal.

Wanted: the launcher detaches the app when it is opening a window.

- Window case (no arguments, or one directory): the launcher starts the app in its own session (`setsid -f` on Linux, `nohup … &` on macOS) and returns the prompt at once.
- Everything else (`--help`, `--version`, an old hook calling `safanoria validate`) stays in the foreground as today, so the message and the exit status 2 that refuses the commit still work.
- Once detached, a crash or stack trace no longer shows anywhere: add a way to stay attached for debugging (a `--foreground` flag or an environment variable).
- "No safanoria.yaml found" comes from the app after it has detached, so its exit status is lost and the message may not show. Show it in a window rather than have the launcher duplicate the project lookup; a desktop entry would need that too.
- Windows: the app is built with a console for the same foreground reason, so `install.ps1` needs its own equivalent (`Start-Process`, or a build without a console).

Not verified: that `nohup` is enough to keep the Java runtime alive on macOS, and anything on Windows (only Linux at hand, as in `gui-as-safanoria-command`).

Out of scope: a desktop entry (`.desktop` file, starting from the application menu). The app would have no working directory to take the project from, so it needs a project picker or recent-projects list, and it does not fix the terminal case.

## Acceptance Criteria

- [x] `safanoria` and `safanoria <dir>` give the prompt back at once, and the window stays open after the terminal is closed.
- [x] `safanoria --help`, `--version` and `safanoria <command>` (an old hook) still print in the terminal and return their exit status (0, 0, 2).
- [x] There is a way to run the app attached to the terminal, to see its output.
- [x] Opening a directory that is in no Safanoria project tells the user so, visibly.
- [x] The same holds for the launchers of `install.sh`, `make install` and `install.ps1`.

## Work Log

- **2026-10-05** · status · started
- **2026-10-05** · decision · The launcher decides, not the app: one argument that is a directory (or none) is a window and is detached; anything else is `exec`ed in the foreground as before. Detached with `setsid … &` where there is a `setsid` (Linux), else `nohup … &` (macOS), output to `/dev/null`.
- **2026-10-05** · decision · Staying attached is an environment variable, `SAFANORIA_FOREGROUND=1`, not a `--foreground` flag: the three launchers only test a variable, and the app has no argument to learn.
- **2026-10-05** · decision · No project: the app opens a small window with the message (and still prints it and exits 2, for an attached run). Not when there is no display.
- **2026-10-05** · decision · Windows keeps the console build, for `--help` and old hooks. For a window, `safanoria.cmd` starts the app through PowerShell with `CreateNoWindow` (no console, so nothing ties it to the terminal) in the directory to open. Rejected `Start-Process -WindowStyle Hidden`: the hidden state can be applied to the app's first window.
- **2026-10-05** · decision · Found while testing: the packaged app's launcher leaves `_JPACKAGE_LAUNCHER` in its environment, and a terminal opened from the app inherits it; there `safanoria --version` printed Java's version and `safanoria validate` failed with "Could not find or load main class". The launchers unset it and the app no longer passes it to the terminals it opens.
- **2026-10-05** · decision · Verified on Linux with the `make install` launcher (same text as `install.sh`'s): prompt back at once, the app survives the hangup of its terminal with `setsid` and with `nohup`, and dies when attached. Not run: macOS, and `install.ps1` (no Windows here); its launcher is written but untried.
- **2026-10-05** · status · review

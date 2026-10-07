---
id: gui-project-icon
type: feature
title: "GUI shows the project's icon instead of Safanoria's"
status: in-progress
priority: medium
size: S
created: 2026-10-07
updated: 2026-10-07
---

## Objective

The GUI always shows Safanoria's own icon (window, taskbar, title bar): `rememberAppIcon()` in `gui/.../theme/Icon.kt` loads the bundled `safanoria-icon-small.svg`, and `Main.kt` uses it for every window. It should show the icon of the app whose tickets are open instead, so that with several projects open at once each window is recognisable in the taskbar and window switcher; today they all look the same.

Safanoria's icon stays as the fallback when the project has none.

The project's icon comes from a new optional key of `safanoria.yaml`, `icon`: the path of an image file (SVG or PNG) relative to the repository root (SPEC §2, schema).

## Acceptance Criteria

- [x] `icon` is in SPEC §2 and the schema, and read into `Config`
- [x] With `icon` set, the window (taskbar, window switcher) and the board's bar show that image, SVG or PNG
- [x] Without `icon`, or when the file is missing or isn't an image, the app shows Safanoria's icon

## Work Log

- **2026-10-07** · status · started
- **2026-10-07** · decision · The icon comes from a new optional `icon` key in `safanoria.yaml` (the user's choice) rather than a conventional location: projects keep their icon where it already is. An optional key old tools ignore, so the spec version stays 1 (§13).
- **2026-10-07** · decision · An `icon` that can't be read or decoded is not a `validate` problem: the app falls back to Safanoria's icon silently. The spec says so, so that a tool that doesn't support a format isn't wrong.
- **2026-10-07** · decision · The icon is read once at launch, from this checkout's file; changing it needs the app reopened. SVG is chosen by the `.svg` extension, anything else is decoded as a raster image.

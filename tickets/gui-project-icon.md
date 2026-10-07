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

Open: where the project's icon comes from. `safanoria.yaml` has no setting for it today (SPEC §2); candidates are a new optional key pointing at an image file in the repository, or a conventional location. A new key is a spec and schema change.

## Acceptance Criteria

## Work Log

- **2026-10-07** · status · started

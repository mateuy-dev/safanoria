---
id: windows-gui-merge-test-fails
type: bug
title: "Windows CI: gui merge test fails"
status: done
priority: medium
size: S
created: 2026-10-06
updated: 2026-10-07
resolvedIn:
  safanoria: 0.4.0
---

## Objective

The `cli` workflow fails on `windows-2022` since 2026-10-06 (first seen on `main` at commit `1a04076`, run 37462898158; Linux and macOS pass): `TicketStoreActionsTest > mergeLandsATicketInReviewAndRemovesItsWorktreeAndBranch` fails with `org.junit.ComparisonFailure at TicketStoreActionsTest.kt:126` in `:gui:allTests`. The last green Windows run on `main` was 2026-10-05 (`README: the CI example pins v0.2.0`), so it came in with what was merged after that. Not known yet whether the test or the app's merge (removing the worktree and branch) is wrong on Windows. Found while working on issue-branch-fails-validate, which doesn't touch `gui`.

### Steps to reproduce

1. Push any commit that runs the `cli` workflow.
2. Look at the `windows-2022` job.

### Expected

`./gradlew allTests` passes on Windows.

### Actual

`TicketStoreActionsTest[jvm] > mergeLandsATicketInReviewAndRemovesItsWorktreeAndBranch[jvm] FAILED`.

<!-- Error messages, refs (e.g. sentry) and the version where it happens. Screenshots and logs: attachments/<id>/ (SPEC §7.8), without personal data. -->

## Acceptance Criteria

- [ ] The `windows-2022` job of the `cli` workflow passes `./gradlew allTests`.

## Work Log

- **2026-10-07** · status · started
- **2026-10-07** · decision · The test was wrong, not the merge: Git for Windows has `core.autocrlf=true`, so `code.txt` lands in the main checkout as `work\r\n` and the comparison with `work\n` fails (reproduced on Linux with a global `core.autocrlf=true`). The test repository now sets `core.autocrlf=false`, as core's `GitFixture` does. The app is left alone: which line endings a checkout gets is the user's git setting.
- **2026-10-07** · status · review
- **2026-10-07** · status · done
- **2026-10-07** · release · safanoria 0.4.0

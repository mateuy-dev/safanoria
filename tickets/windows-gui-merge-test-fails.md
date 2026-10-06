---
id: windows-gui-merge-test-fails
type: bug
title: "Windows CI: gui merge test fails"
status: backlog
priority: medium
size: S
created: 2026-10-06
updated: 2026-10-06
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

### Steps to reproduce

1.

### Expected

### Actual

<!-- Error messages, refs (e.g. sentry) and the version where it happens. Screenshots and logs: attachments/<id>/ (SPEC §7.8), without personal data. -->

## Acceptance Criteria

## Work Log

---
id: mobile-ticket-capture
type: research
title: Android app
status: in-progress
priority: medium
size: S
created: 2026-10-02
updated: 2026-10-02
related: [gui-viewer]
---

## Objective

Study how to create new tickets from an Android phone: a screenshot of an error, a quick idea, and similar. The answer may be a native app, but also another route (share sheet, a web form, an issue/PR on the hosting service, a note picked up later by an agent). Compare the options and recommend one.

Things to consider: tickets are files in a git repository, so the phone needs a way to commit (or to hand off to something that commits); screenshots become attachments (SPEC §7.8, under 1 MB, personal data redacted); id choice and validation; the KMP `core` module that `gui-viewer` also plans to reuse.

## Acceptance Criteria

<!-- One question per item, checked when answered. -->

Answer: **no native app for now.** Use Remote Control today (no build), and add an issue form
plus a GitHub Action that turns a labeled issue into a ticket. Revisit an app with `gui-viewer`.

- [x] Which routes can turn a phone capture (screenshot, quick idea) into a ticket, and how do
      they compare? Candidates: native Android app, share-sheet target only, web form / PWA,
      an issue on the hosting service, the GitHub mobile app's file editor, a note or email
      picked up later by an agent. Compare on taps to capture, offline use, screenshot
      handling, auth, commit path, validation, and the cost to build and maintain.
      Table in the Work Log (step 1). Leading: a GitHub issue converted by an Action (cheap,
      any GitHub user) and Claude Code from the Claude app (nothing to build). A native app
      wins only on offline capture and taps, at the highest cost. File editor, web form and
      a note inbox dropped.
- [x] How does the capture become a commit on `mainBranch`: from the phone (GitHub REST
      contents / Git Data API, JGit), or through a hand-off (issue, note) that something else
      commits (a GitHub Action running `safanoria new`, an agent)? Can ticket and screenshot
      land in one commit?
      Through a hand-off that has a full clone: the Action (`fetch-depth: 0`) or the agent.
      Yes, one commit: the Action commits both; an app would need the Git Data API (blobs, tree,
      commit, ref), since the Contents API makes one commit per file. Details in step 2.
- [x] Screenshots: how do they end up under 1 MB and in `<dir>/attachments/<id>/` (SPEC §7.8),
      and where does redaction of personal data (§10) happen: on the phone, or at the hand-off?
      Resized by whatever commits (Action or agent), which also copies it into
      `attachments/<id>/`. Redaction on the phone, before attaching: an issue attachment is
      readable as soon as it's posted and may outlive the issue.
- [x] Ids: who proposes the id and checks it was never used on any branch (§3, §14.3), when
      the phone has no clone? Is a provisional id that the hand-off finalises acceptable?
      The hand-off, with `safanoria new` on its clone; an optional form field can request an id.
      No provisional id: only a phone app would need one, and it would still need a clone to
      finalise it.
- [x] Can the KMP `core` module (parser, `NewTicket`, validator) run on Android? Which
      dependencies support an Android target, and which parts need `git` through the shell
      and so can't run there?
      Yes, it compiles for Android unchanged (JVM sources as `androidMain`); every dependency
      resolves. Not usable there: `Git`, `Branches`, `BranchView`, `GitTreeFileSystem`,
      `Resume`, `Hooks`. `Repository`, the parser and `NewTicket.prepare` work on any
      `FileSystem`. Step 3.
- [x] Recommendation: which route, the smallest first version, and the follow-up tickets.
      Above. Smallest first version: the README section on Remote Control (done here), then
      `issue-to-ticket`, which needs `safanoria new` to attach a file and print the id
      (`new-attach`).

## Plan

Mostly desk research, plus two small spikes. Throwaway code goes in `spike/` and is deleted in
the last commit, so only the ticket merges. Nothing is pushed or posted to GitHub without the
user's OK (an API spike would write to a repository).

- [x] Routes: list the candidate routes and compare them in a table (criteria from the first
      question). Drop the ones that clearly lose and say why.
- [x] Commit path: check what the GitHub REST API needs to commit a ticket plus an image in one
      commit (Git Data API: blobs, tree, commit, ref update) and the auth (fine-grained token vs
      GitHub App). Sketch the hand-off alternative: an issue with a label, turned into a ticket
      by a GitHub Action that runs `safanoria new` and moves the issue's image into
      attachments. Ask the user before trying either against a real repository.
- [x] Core on Android: in `spike/`, add an `androidTarget` (Android SDK is in
      `~/Android/Sdk`) to a copy of `core`'s build, see which dependencies resolve (kaml,
      json-schema-validator, okio) and which sources fail to compile (`Pipe`, `Git`). Record
      what an app could reuse.
- [x] Ids and screenshots: decide where id suggestion, uniqueness check, image resize and
      redaction happen for the leading routes.
- [ ] Answer every question; write Learnings with a target for each (SPEC.md, skill, README,
      or a new ticket in `related`). Delete `spike/`.

## Learnings

<!-- The answers, each resolved (SPEC §7.6): promoted, a new ticket, or ticket only. Work that follows becomes new tickets in related, never children. -->

- From a phone, Claude Code's Remote Control already works: the session runs on the user's
  machine, and photos attached in the Claude app are saved under `~/.claude/uploads/`, so the
  agent can copy them into attachments. Cloud sessions commit on their own branch instead.
  → promoted: README.md
- A GitHub issue form plus an Action is the cheapest real capture route. The Action needs a
  full clone (`fetch-depth: 0`) to check ids; downloads images from the signed `<img src>` in
  `body_html` (plain `user-attachments` URLs need a browser session in private
  repositories); gets issue text only through `env` (script injection); runs only on a label
  or for OWNER/MEMBER; opens a PR when `mainBranch` is protected; and a `GITHUB_TOKEN` push
  doesn't start other workflows.
- Issue attachments are readable as soon as they're posted and may outlive the issue, so
  redaction happens on the phone, before attaching.
- `safanoria new` can't attach a file (copy into `attachments/<id>/` and link it) and doesn't
  print the chosen id in a machine-readable form (`--format json`, as `resume` has).
- Core compiles for Android unchanged (JVM sources as `androidMain`, AGP KMP library plugin);
  okio, kaml, json-schema-validator and their dependencies resolve through `-jvm` artifacts.
  Git-dependent parts (`Git`, `Branches`, `BranchView`, `GitTreeFileSystem`, `Resume`, `Hooks`)
  can't run there; `NewTicket.prepare` returns files instead of writing them, so an app could
  commit them through the Git Data API. The spike build is in commit 54f44c8, for whoever
  plans an app (see `gui-viewer`).
  → ticket only
- AGP 9.1 requires Gradle ≥ 9.3.1; this repository's wrapper is 9.3.0.
  → ticket only
- Without a clone, nothing can check that an id was never used on any branch (§3) short of
  walking history through the API; keep id choice wherever a clone is.
  → ticket only
- `safanoria validate` reads any Plan item that starts with a backticked word as a child item
  (`` - [ ] `core` on Android `` → `ref-unknown`), while SPEC §7.5 says a child item starts
  with a ticket id in backticks.

## Work Log

- **2026-10-02** · status · started
- **2026-10-02** · plan · Questions and plan written. Routes compared on paper first; spikes only for `core` on Android and, with the user's OK, the GitHub commit path.
- **2026-10-02** · step 1 · Routes compared (desk research, sources: GitHub changelog/blog on
  GitHub Mobile, code.claude.com/docs/en/mobile). Taps counted from the screenshot or the idea
  to "sent".

  | Route | Taps | Offline | Screenshot | Auth | Commit path | Validation | Build / maintain |
  |---|---|---|---|---|---|---|---|
  | A. Native app (KMP, reuses `core`) | 3 (share → form → send) | queue, send later | resize and crop on device | token or OAuth on the phone | GitHub API from the phone | full, minus the branch check | high: app, store, updates |
  | B. Share-target-only app | 2–3 | queue | resize on device, no crop | token on the phone | GitHub API, or opens an issue (D) | none on device | medium |
  | C. Web form / PWA (Web Share Target) | 3 | no | browser resize | token in the browser or a backend | GitHub API or backend | in a backend only | medium, plus hosting |
  | D. GitHub issue (GitHub Mobile, issue form) → Action runs `safanoria new` | ~6 | no (drafts only) | issue attachment, the Action downloads it | the user's existing GitHub login | Action commits on `mainBranch` | Action runs validate, comments back | low: one workflow |
  | E. GitHub Mobile file editor | many | no | none: text files only | existing login | direct commit | none, frontmatter by hand | none |
  | F. Claude app → Claude Code cloud session (or Remote Control) running the skill | ~4 + a confirmation | no | photo attached to the message | claude.ai and GitHub connected | agent commits; cloud sessions push a session branch, not `mainBranch` | full, the CLI runs in the session | none: setup only |
  | G. Note or email inbox, processed later by an agent | 2 | yes | attachment in the note/email | per inbox | agent commits in a batch | full, at processing time | low, but one more moving part |

  Dropped: E (no images, ids and frontmatter by hand, nothing checked). C (needs hosting and
  holds a write token, and does what B does with more taps). G loses to D: the same deferred
  processing without structure or a place to report errors. B is A without the UI that would make
  it worth installing; keep it as A's first version if A is chosen. Leading: **D** (cheapest
  real route, works for anyone with GitHub access) and **F** (no build at all, but needs a
  Claude plan and lands on a session branch). **A** only if a phone app is wanted anyway
  (e.g. alongside `gui-viewer`). To check: images attached on Android are reported dropped in
  Remote Control sessions (public issue reports); unverified for cloud sessions.
- **2026-10-02** · step 2 · Commit path, on paper. Not tried against a real repository: the
  answer doesn't depend on it, and the remaining unknowns are about the phone apps (checked by
  hand in the Learnings).
  - From the phone (A, B): the Contents API (`PUT /repos/{o}/{r}/contents/{path}`) makes one
    commit per file, so ticket and screenshot would be two commits. The Git Data API does it in
    one: `POST git/blobs` per file (image base64), `POST git/trees` with `base_tree`,
    `POST git/commits`, `PATCH git/refs/heads/<mainBranch>`; a race with another push fails the
    ref update, and the app retries. Auth: a fine-grained token with Contents read/write on the
    repository, stored on the phone; a GitHub App with device flow avoids pasting a token but is
    more to build. Without a clone, the phone can't check that an id was never used on any
    branch (§14.3) without many API calls.
  - Hand-off (D): an issue labeled e.g. `ticket` starts a workflow (`on: issues: [labeled]`,
    `permissions: contents: write, issues: write`) that checks out with `fetch-depth: 0` (all
    branches, so `safanoria new` checks ids for real), reads the issue form fields, downloads
    images from the signed `<img src>` URLs in `body_html` (`Accept:
    application/vnd.github.html+json`; the plain `user-attachments` URLs need a browser session
    in private repositories), runs `safanoria new`, copies the image into
    `<dir>/attachments/<id>/`, runs `safanoria validate`, commits ticket and image in one
    commit on `mainBranch`, comments the link and closes the issue. Constraints found:
    issue text is untrusted input, so it goes to scripts through `env`, never `${{ }}` inside
    `run`; only a label (needs triage rights) or `author_association` OWNER/MEMBER should
    trigger it, or anyone could add tickets to a public repository; a push with `GITHUB_TOKEN`
    doesn't start other workflows, and a protected `mainBranch` needs a PR (auto-merge) instead
    of a push.
  - `safanoria new` lacks two things this needs: a way to attach a file (copy into
    `attachments/<id>/` and link it) and machine-readable output of the chosen id
    (`--format json`, as `resume` has). Today a script would run `--dry-run` and parse the id.
  - F: a cloud session commits on its own session branch, which then needs a PR into
    `mainBranch`; through Remote Control the session is on the user's machine and can commit on
    `mainBranch` like any local session (`--on main`).
- **2026-10-02** · step 3 · Core on Android: `spike/` builds core's own sources (common +
  `jvmMain` as `androidMain`) with the `com.android.kotlin.multiplatform.library` plugin.
  `compileAndroidMain` succeeds unchanged. AGP 9.1 refused Gradle 9.3.0 (needs 9.3.1), so the
  spike uses AGP 8.13.2 (`androidLibrary {}` DSL; AGP 9 calls it `android {}`). Every dependency
  resolves: okio, kaml (+ snakeyaml-engine-kmp), json-schema-validator (+ normalize,
  karacteristics, codepoints) through their `-jvm` artifacts, uri-kmp through its `-android`
  one. The linuxX64 `libunistring` problem doesn't exist there (JVM artifact). Only compiled,
  not run on a device or emulator: the libraries are plain Kotlin/JVM, the risk is low, and an
  app ticket would test it first. Reusable without git: `Repository` (on any Okio
  `FileSystem`, `git` is lazy), the parser, the validator's per-file checks and
  `NewTicket.prepare(repository, request, today, takenIds)`, which returns the files to write
  instead of writing them, so an app can feed it tickets fetched from the API (in a
  `FakeFileSystem`-like tree) and commit the result through the Git Data API. Needs git, so not
  usable on a phone: `Git`, `Branches`, `BranchView`, `GitTreeFileSystem`, `Resume`, `Hooks`
  (Android has `sh` and `ProcessBuilder`, but no `git`).
- **2026-10-02** · step 4 · Ids and screenshots, per leading route.
  - Ids. D: the Action has a full clone, so `safanoria new` suggests the id from the issue title
    and checks it as on a laptop; an optional "id" form field overrides it, and a refusal is
    commented on the issue (the user fixes the field and relabels). No provisional id needed.
    F: the skill as it is (dry run, confirm the id in the chat). A: the phone can list ticket
    files and branch names through the API, but not every file that ever existed on any branch
    (§3: never reused) without walking history; it would need a provisional id finalised by
    something with a clone, which is D's Action again. So the clone-based check stays off the
    phone in every route.
  - Size. D: the Action shrinks any image over 1 MB (ImageMagick `-resize` / quality, installed
    in the job if the runner lacks it) and names it lowercase without spaces. F: the agent does
    the same in the session. A: the app would resize before upload.
  - Redaction (§10). Can't be automated reliably, so it happens on the phone before the image
    leaves it: Android's screenshot editor crops and draws over. In D this is also the only
    safe place: the issue attachment is visible to everyone who can read the repository from
    the moment the issue is posted, and the `user-attachments` asset may stay reachable after
    the issue is closed or edited. The issue form says so next to the upload field.
  - `requests`: the person filing from the phone is the maintainer, not a user request. When
    the capture is a user's report, optional form fields (user id, channel, verbatim quote) map
    to `requests` and User Requests; the user id is the project's id (§10), never a name.
- **2026-10-02** · step 5 · Questions answered, README gets a "From a phone" paragraph, `spike/`
  deleted. Four learnings wait for follow-up tickets (`issue-to-ticket`, `new-attach`,
  `plan-child-detection`), whose ids the user confirms before they are created on `main`.

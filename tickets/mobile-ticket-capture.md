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

- [ ] Which routes can turn a phone capture (screenshot, quick idea) into a ticket, and how do
      they compare? Candidates: native Android app, share-sheet target only, web form / PWA,
      an issue on the hosting service, the GitHub mobile app's file editor, a note or email
      picked up later by an agent. Compare on taps to capture, offline use, screenshot
      handling, auth, commit path, validation, and the cost to build and maintain.
- [ ] How does the capture become a commit on `mainBranch`: from the phone (GitHub REST
      contents / Git Data API, JGit), or through a hand-off (issue, note) that something else
      commits (a GitHub Action running `safanoria new`, an agent)? Can ticket and screenshot
      land in one commit?
- [ ] Screenshots: how do they end up under 1 MB and in `<dir>/attachments/<id>/` (SPEC §7.8),
      and where does redaction of personal data (§10) happen: on the phone, or at the hand-off?
- [ ] Ids: who proposes the id and checks it was never used on any branch (§3, §14.3), when
      the phone has no clone? Is a provisional id that the hand-off finalises acceptable?
- [ ] Can the KMP `core` module (parser, `NewTicket`, validator) run on Android? Which
      dependencies support an Android target, and which parts need `git` through the shell
      and so can't run there?
- [ ] Recommendation: which route, the smallest first version, and the follow-up tickets.

## Plan

Mostly desk research, plus two small spikes. Throwaway code goes in `spike/` and is deleted in
the last commit, so only the ticket merges. Nothing is pushed or posted to GitHub without the
user's OK (an API spike would write to a repository).

- [x] Routes: list the candidate routes and compare them in a table (criteria from the first
      question). Drop the ones that clearly lose and say why.
- [ ] Commit path: check what the GitHub REST API needs to commit a ticket plus an image in one
      commit (Git Data API: blobs, tree, commit, ref update) and the auth (fine-grained token vs
      GitHub App). Sketch the hand-off alternative: an issue with a label, turned into a ticket
      by a GitHub Action that runs `safanoria new` and moves the issue's image into
      attachments. Ask the user before trying either against a real repository.
- [ ] Core on Android: in `spike/`, add an `androidTarget` (Android SDK is in
      `~/Android/Sdk`) to a copy of `core`'s build, see which dependencies resolve (kaml,
      json-schema-validator, okio) and which sources fail to compile (`Pipe`, `Git`). Record
      what an app could reuse.
- [ ] Ids and screenshots: decide where id suggestion, uniqueness check, image resize and
      redaction happen for the leading routes.
- [ ] Answer every question; write Learnings with a target for each (SPEC.md, skill, README,
      or a new ticket in `related`). Delete `spike/`.

## Learnings

<!-- The answers, each resolved (SPEC §7.6): promoted, a new ticket, or ticket only. Work that follows becomes new tickets in related, never children. -->

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

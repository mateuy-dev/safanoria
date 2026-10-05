"""Generates core/src/commonTest/fixtures/validate/<case>/: a mini repository per SPEC §12 rule.

Run: python3 tools/make_validate_fixtures.py   (rewrites the directory; edit cases here, not the files)

Each case: safanoria.yaml, tickets/*.md, expected.txt ("<file>:<line> <code>", lines found by
searching marker text, never counted by hand), optional only.txt (only-given-files mode).
"""
import pathlib, shutil, sys

repo = pathlib.Path(sys.argv[1]) if len(sys.argv) > 1 else pathlib.Path(__file__).resolve().parent.parent
out = repo / "core/src/commonTest/fixtures/validate"
shutil.rmtree(out, ignore_errors=True)

CONFIG1 = """safanoria: 1
components:
  app:
    version: { file: gradle.properties, property: version }
channels: [email, whatsapp]
"""
CONFIG2 = """safanoria: 1
components:
  app:
    version: { file: gradle.properties, property: version }
  server:
    external: true
"""
CONFIG_TAGS = CONFIG1 + """tags:
  registry: Official registry integration
  offline: Working without a connection
"""


def ticket(id, status="backlog", type="feature", front="", objective="Why.", requests_section=None,
           ac="", plan="", design=None, learnings=None, log="", extra_sections="", sections=None):
    """A ticket; sections=[(name, body), ...] overrides the standard layout."""
    fm = f"""---
id: {id}
type: {type}
title: Ticket {id}
status: {status}
priority: medium
size: S
created: 2026-10-01
updated: 2026-10-01
{front}---
"""
    if sections is None:
        sections = [("Objective", objective)]
        if requests_section is not None:
            sections.append(("User Requests", requests_section))
        sections += [("Acceptance Criteria", ac), ("Plan", plan)]
        if design is not None:
            sections.append(("Design", design))
        if learnings is not None:
            sections.append(("Learnings", learnings))
        sections.append(("Work Log", log))
    body = "".join(f"\n## {name}\n\n{text + chr(10) if text else ''}" for name, text in sections)
    return fm + body + extra_sections


LOG = "- **2026-10-01** · status · Started."
DONE = dict(status="done", ac="- [x] It works", plan="- [x] Do it", log=LOG)

cases = {}


def case(name, tickets, expect, config=CONFIG1, only=None, files=None):
    """expect: [(file, marker text, code)]; file 'safanoria.yaml' or a ticket id.
    files: other files, {path under tickets/: text}, e.g. attachments."""
    cases[name] = (tickets, expect, config, only, files or {})


# --- valid repositories ------------------------------------------------------------------------
case("valid", {
    "herd-locations": ticket("herd-locations", status="in-progress", ac="- [ ] Map shows herds",
                             plan="- [x] `herd-locations-model`: first.\n- [ ] `herd-locations-map`: then.\n- [ ] Own step.",
                             log=LOG),
    "herd-locations-model": ticket("herd-locations-model", front="parent: herd-locations\nresolvedIn:\n  app: 1.0.0\n", **DONE),
    "herd-locations-map": ticket("herd-locations-map", front="parent: herd-locations\nblockedBy: [herd-locations-model]\nrelated: [map-tiles-study]\n"),
    "map-tiles-study": ticket("map-tiles-study", type="research", status="done", ac="- [x] Which provider?", plan="- [x] Compare",
                              learnings="- Tiles are rate-limited.\n  → new ticket: `herd-locations-map`\n- Fine.\n  → ticket only", log=LOG),
    "user-request": ticket("user-request", front="requests:\n  - user: 1834\n    channel: whatsapp\n    date: 2026-09-28\n",
                           requests_section="> Vull veure els ramats.\n— user 1834 · whatsapp · 2026-09-28"),
    "closed": ticket("closed", status="wontfix", front="tags: [registry]\n", log="- **2026-10-01** · status · Not needed."),
}, [], config=CONFIG_TAGS)
case("extra-sections", {
    "custom": ticket("custom", sections=[("Original document", "Pasted."), ("Objective", "Why."), ("Acceptance Criteria", ""),
                                         ("Notes", "Anything."), ("Plan", ""), ("Work Log", "")]),
}, [])

# --- ids (§3) ----------------------------------------------------------------------------------
case("id-mismatch", {"map-input": ticket("map-other")}, [("map-input", "id: map-other", "id-mismatch")])
case("id-invalid", {"map-input": ticket("Map_Input")},
     [("map-input", "id: Map_Input", "schema-pattern"), ("map-input", "id: Map_Input", "id-mismatch")])
case("id-duplicate", {"map-one": ticket("map-one"), "map-two": ticket("map-one")},
     [("map-one", "id: map-one", "id-duplicate"), ("map-two", "id: map-one", "id-duplicate"), ("map-two", "id: map-one", "id-mismatch")])

# --- fields (§5) -------------------------------------------------------------------------------
case("required-field", {"no-title": ticket("no-title").replace("title: Ticket no-title\n", "")}, [("no-title", "id: no-title", "schema-required")])
case("enum", {"bad-status": ticket("bad-status", status="blocked")}, [("bad-status", "status: blocked", "schema-enum")])
case("date", {"bad-date": ticket("bad-date").replace("created: 2026-10-01", "created: 2026-1-1")}, [("bad-date", "created: 2026-1-1", "schema-pattern")])
case("version", {"bad-version": ticket("bad-version", front="resolvedIn:\n  app: \"4.3\"\n", **DONE)}, [("bad-version", "app: \"4.3\"", "schema-pattern")])

# --- components (§2, §9) -------------------------------------------------------------------------
case("area-unknown-component", {"web-only": ticket("web-only", front="area: [web]\n")}, [("web-only", "area: [web]", "area-unknown-component")])
case("area-required", {"no-area": ticket("no-area")}, [("no-area", "id: no-area", "area-required")], config=CONFIG2)
case("resolved-in-unknown-component", {"stamped": ticket("stamped", front="resolvedIn:\n  web: 1.0.0\n", **DONE)},
     [("stamped", "web: 1.0.0", "resolved-in-unknown-component")])
case("resolved-in-not-in-area", {"stamped": ticket("stamped", front="area: [app]\nresolvedIn:\n  server: 1.0.0\n", **DONE)},
     [("stamped", "server: 1.0.0", "resolved-in-not-in-area")], config=CONFIG2)
case("resolved-in-not-done", {"early": ticket("early", status="review", front="resolvedIn:\n  app: 1.0.0\n", ac="- [x] A", plan="- [x] B", log=LOG)},
     [("early", "resolvedIn:", "resolved-in-not-allowed")])
case("resolved-in-research", {"study": ticket("study", type="research", front="resolvedIn:\n  app: 1.0.0\n", **DONE)},
     [("study", "resolvedIn:", "resolved-in-not-allowed")])

# --- references (§8) ---------------------------------------------------------------------------
case("ref-unknown", {
    "refs": ticket("refs", status="in-progress", front="parent: no-parent\nblockedBy: [no-blocker]\nrelated: [no-related]\n",
                   ac="- [ ] A", plan="- [ ] `no-child`: x", learnings="- Found.\n  → new ticket: `no-new`", log=LOG),
}, [("refs", "parent: no-parent", "ref-unknown"), ("refs", "blockedBy: [no-blocker]", "ref-unknown"),
    ("refs", "related: [no-related]", "ref-unknown"), ("refs", "`no-child`", "ref-unknown"), ("refs", "`no-new`", "ref-unknown")])
case("blocked-by-cycle", {"first": ticket("first", front="blockedBy: [second]\n"), "second": ticket("second", front="blockedBy: [first]\n")},
     [("first", "blockedBy: [second]", "blocked-by-cycle"), ("second", "blockedBy: [first]", "blocked-by-cycle")])

# --- parents and children (§8.1, §7.5, §9) -------------------------------------------------------
case("parent-nested", {
    "top": ticket("top", plan="- [ ] `middle`: x"),
    "middle": ticket("middle", front="parent: top\n", plan="- [ ] `bottom`: x"),
    "bottom": ticket("bottom", front="parent: middle\n"),
}, [("middle", "parent: top", "parent-nested")])
case("research-parent", {
    "study": ticket("study", type="research", plan="- [ ] `follow-up`: x"),
    "follow-up": ticket("follow-up", front="parent: study\n"),
}, [("study", "id: study", "research-parent")])
case("parent-plan-missing-child", {
    "parent-one": ticket("parent-one", plan="- [ ] Own step."),
    "child-one": ticket("child-one", front="parent: parent-one\n"),
}, [("parent-one", "## Plan", "parent-plan-missing-child")])
case("parent-plan-duplicate-child", {
    "parent-one": ticket("parent-one", plan="- [ ] `child-one`: first.\n- [ ] `child-one`: again."),
    "child-one": ticket("child-one", front="parent: parent-one\n"),
}, [("parent-one", "`child-one`: again.", "parent-plan-duplicate-child")])
case("plan-item-not-child", {
    "parent-one": ticket("parent-one", plan="- [ ] `stranger`: not mine."),
    "stranger": ticket("stranger"),
}, [("parent-one", "`stranger`", "plan-item-not-child")])
case("child-check-mismatch", {
    "parent-one": ticket("parent-one", status="in-progress", ac="- [ ] A", plan="- [ ] `child-done`: x\n- [x] `child-open`: y", log=LOG),
    "child-done": ticket("child-done", front="parent: parent-one\n", **DONE),
    "child-open": ticket("child-open", front="parent: parent-one\n"),
}, [("parent-one", "`child-done`", "child-check-mismatch"), ("parent-one", "`child-open`", "child-check-mismatch")])
case("child-resolved-later", {
    "parent-one": ticket("parent-one", front="resolvedIn:\n  app: 1.2.0\n", status="done", ac="- [x] A", plan="- [x] `child-one`: x", log=LOG),
    "child-one": ticket("child-one", front="parent: parent-one\nresolvedIn:\n  app: 1.10.0\n", **DONE),
}, [("child-one", "app: 1.10.0", "child-resolved-later")])

# --- sections (§7) -----------------------------------------------------------------------------
case("section-missing", {
    "no-objective": ticket("no-objective", sections=[("Acceptance Criteria", ""), ("Work Log", "")]),
    "no-plan": ticket("no-plan", sections=[("Objective", "Why."), ("Work Log", "")]),  # Plan and AC are optional
}, [("no-objective", "## Acceptance Criteria", "section-missing")])
case("section-order", {"swapped": ticket("swapped", sections=[("Objective", "Why."), ("Plan", ""), ("Acceptance Criteria", ""), ("Work Log", "")])},
     [("swapped", "## Acceptance Criteria", "section-order")])
case("section-duplicate", {"twice": ticket("twice", sections=[("Objective", "Why."), ("Acceptance Criteria", ""), ("Plan", ""), ("Plan", ""), ("Work Log", "")])},
     [("twice", "## Work Log", "section-duplicate")])  # marker fixed below: the second Plan heading
case("section-empty", {
    "started-empty": ticket("started-empty", status="in-progress", log="<!-- fill in -->"),
    "ready-no-ac": ticket("ready-no-ac", status="ready"),  # Acceptance Criteria may stay empty
    "review-loose-ends": ticket("review-loose-ends", status="review", plan="- [ ] Two", learnings="- Something learned.", log=LOG),
}, [("started-empty", "## Work Log", "section-empty")])

# --- requests (§7.3, §5) -----------------------------------------------------------------------
case("requests-quotes-mismatch", {
    "asked": ticket("asked", front="requests:\n  - user: 1\n    channel: email\n    date: 2026-09-28\n  - user: 2\n    channel: email\n    date: 2026-09-29\n",
                    requests_section="> Please.\n— user 1 · email · 2026-09-28"),
}, [("asked", "## User Requests", "requests-quotes-mismatch")])
case("channel-unknown", {
    "faxed": ticket("faxed", front="requests:\n  - user: 1\n    channel: fax\n    date: 2026-09-28\n",
                    requests_section="> Please.\n— user 1 · fax · 2026-09-28"),
}, [("faxed", "  - user: 1", "channel-unknown")])

# --- tags (§2, §5) -------------------------------------------------------------------------------
case("tag-unknown", {"themed": ticket("themed", front="tags: [registry, official-registry]\n")},
     [("themed", "tags: [registry, official-registry]", "tag-unknown")], config=CONFIG_TAGS)
case("tag-undeclared", {"themed": ticket("themed", front="tags: [registry]\n")}, [("themed", "tags: [registry]", "tag-unknown")])

# --- config and parsing ------------------------------------------------------------------------
case("config-schema", {"fine": ticket("fine")}, [("safanoria.yaml", "components: {}", "schema-minProperties")],
     config="safanoria: 1\ncomponents: {}\n")
case("parse-errors", {
    "broken-yaml": ticket("broken-yaml").replace("priority: medium", "priority: [medium"),
    "broken-log": ticket("broken-log", log="- 2026-10-01 started"),
}, [("broken-yaml", "size: S", "yaml-syntax"),  # kaml reports where it notices: the line after the open '['
    ("broken-log", "- 2026-10-01 started", "work-log-entry")])

# --- attachments (§7.8) ------------------------------------------------------------------------
case("attachment-missing", {
    "crash-on-save": ticket("crash-on-save", objective="\n".join([
        "Crash: ![found](attachments/crash-on-save/crash.png) and [log](attachments/crash-on-save/missing.log).",
        "Other ticket's file: ![gone](./attachments/other-ticket/gone.png \"title\")",
        "Not checked: [web](https://example.com/a.png), [code](../README.md), `[in code](attachments/x/y.png)`",
        "```",
        "![in a fence](attachments/crash-on-save/fenced.png)",
        "```",
    ])),
}, [("crash-on-save", "missing.log", "attachment-missing"), ("crash-on-save", "gone.png", "attachment-missing")],
   files={"attachments/crash-on-save/crash.png": "png"})

# --- only-given-files mode ---------------------------------------------------------------------
case("only-given-files", {
    "parent-one": ticket("parent-one", status="in-progress", ac="- [ ] A", plan="- [ ] `child-one`: x", log=LOG),
    "child-one": ticket("child-one", front="parent: parent-one\n", **DONE),
    "unrelated": ticket("unrelated", status="blocked"),
}, [("parent-one", "`child-one`", "child-check-mismatch")], only=["tickets/child-one.md"])


def line_of(text, marker, nth=1):
    hits = [i + 1 for i, l in enumerate(text.split("\n")) if marker in l]
    if len(hits) < nth:
        sys.exit(f"marker not found: {marker!r}")
    return hits[nth - 1]


for name, (tickets, expect, config, only, files) in cases.items():
    root = out / name
    (root / "tickets").mkdir(parents=True)
    (root / "safanoria.yaml").write_text(config)
    for tid, text in tickets.items():
        (root / "tickets" / f"{tid}.md").write_text(text)
    for path, text in files.items():
        (root / "tickets" / path).parent.mkdir(parents=True, exist_ok=True)
        (root / "tickets" / path).write_text(text)
    lines = []
    for file, marker, code in expect:
        if file == "safanoria.yaml":
            path, text = "safanoria.yaml", config
        else:
            path, text = f"tickets/{file}.md", tickets[file]
        nth = 1
        if name == "section-duplicate":
            marker, nth = "## Plan", 2
        if name == "parse-errors" and file == "broken-yaml":
            nth = 1
        lines.append(f"{path}:{line_of(text, marker, nth)} {code}")
    (root / "expected.txt").write_text("".join(l + "\n" for l in sorted(lines)))
    if only:
        (root / "only.txt").write_text("".join(o + "\n" for o in only))
print(len(cases), "fixtures")

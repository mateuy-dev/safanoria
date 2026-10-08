package dev.mateuy.safanoria.core

import okio.Path.Companion.toPath
import okio.fakefilesystem.FakeFileSystem
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue

class NewTicketTest {
    private val root = "/repo".toPath()
    private val today = "2026-10-02"

    // Plain concatenation: trimIndent would see the inserted Plan's lines and keep headings indented.
    private fun ticket(id: String, front: String = "", plan: String = "") =
        "---\nid: $id\ntype: feature\ntitle: Ticket $id\nstatus: backlog\npriority: medium\nsize: S\n" +
            "created: 2026-10-01\nupdated: 2026-10-01\n$front---\n\n## Objective\n\nWhy.\n\n" +
            "## Acceptance Criteria\n\n## Plan\n\n" + (if (plan.isEmpty()) "" else "$plan\n\n") + "## Work Log\n"

    private fun repo(components: String = "  app:\n    external: true\n", template: String? = null): Pair<FakeFileSystem, Repository> {
        val fs = FakeFileSystem()
        fs.createDirectories(root / "tickets")
        fs.write(root / "safanoria.yaml") { writeUtf8("safanoria: 1\nworktree: ../project--{id}\ncomponents:\n$components") }
        fs.write(root / "tickets" / "herd-locations.md") { writeUtf8(ticket("herd-locations", plan = "- [ ] Own step.\n      Detail.")) }
        fs.write(root / "tickets" / "empty-plan.md") { writeUtf8(ticket("empty-plan")) }
        fs.write(root / "tickets" / "study.md") { writeUtf8(ticket("study").replace("type: feature", "type: research")) }
        fs.write(root / "tickets" / "child.md") { writeUtf8(ticket("child", front = "parent: empty-plan\n")) }
        template?.let { t -> fs.write(root / "tickets" / "_TEMPLATE.md") { writeUtf8(t) } }
        return fs to Repository(root, fs)
    }

    private fun write(fs: FakeFileSystem, ready: NewTicketResult.Ready) =
        ready.files.forEach { f -> fs.write(f.path) { writeUtf8(f.text) } }

    @Test
    fun createsFromTheBuiltInTemplate() {
        val (fs, repo) = repo()
        val r = assertIs<NewTicketResult.Ready>(NewTicket.prepare(repo, NewTicketRequest("Map input: pick a place", type = TicketType.MAINTENANCE, size = Size.M, objective = "Users pick a place on a map."), today))
        assertEquals("map-input-pick-place", r.id)
        assertTrue(r.idSuggested)
        val created = r.files.single()
        assertEquals(root / "tickets" / "map-input-pick-place.md", created.path)
        val f = Frontmatter.parse(created.path, created.text).first!!
        assertEquals("Map input: pick a place", f.title!!.value)
        assertEquals(listOf(TicketType.MAINTENANCE, Status.BACKLOG, Size.M), listOf(f.type, f.status, f.size))
        assertEquals("2026-10-02", f.created!!.value)
        assertTrue(created.text.contains("## Objective\n\nUsers pick a place on a map.\n\n## Acceptance Criteria"), created.text)
        write(fs, r)
        assertEquals(emptyList(), Validator(Repository(root, fs)).validate(r.files.map { it.path }).map { it.toString() })
    }

    @Test
    fun tagsMustBeDeclared() {
        val (fs, repo) = repo(components = "  app:\n    external: true\ntags:\n  registry: Official registry integration\n")
        val r = assertIs<NewTicketResult.Ready>(NewTicket.prepare(repo, NewTicketRequest("Send movements", tags = listOf("registry", "registry")), today))
        assertTrue("size: S\ntags: [registry]\ncreated:" in r.files.single().text, r.files.single().text)
        write(fs, r)
        assertEquals(emptyList(), Validator(Repository(root, fs)).validate(r.files.map { it.path }).map { it.toString() })

        val refused = assertIs<NewTicketResult.Refused>(NewTicket.prepare(repo, NewTicketRequest("Other", tags = listOf("official-registry")), today))
        assertTrue("'official-registry' is not a tag" in refused.reason && "registry" in refused.reason, refused.reason)
    }

    @Test
    fun childIsAddedToTheParentPlan() {
        val (fs, repo) = repo()
        val r = assertIs<NewTicketResult.Ready>(NewTicket.prepare(repo, NewTicketRequest("Map input", parent = "herd-locations"), today))
        assertEquals("herd-locations-map-input", r.id)
        val parent = r.files.single { !it.isNew }
        assertTrue(parent.text.contains("- [ ] Own step.\n      Detail.\n- [ ] `herd-locations-map-input`: Map input\n"), parent.text)
        assertTrue(parent.text.contains("updated: 2026-10-02"))
        assertTrue(r.files.single { it.isNew }.text.contains("parent: herd-locations\n"))
        write(fs, r)
        assertEquals(emptyList(), Validator(Repository(root, fs)).validate(r.files.map { it.path }).map { it.toString() })
    }

    @Test
    fun childOfAnEmptyPlan() {
        val (fs, repo) = repo()
        // empty-plan already has `child`, which isn't in its Plan yet: add it too, then check both.
        val r = assertIs<NewTicketResult.Ready>(NewTicket.prepare(repo, NewTicketRequest("Second", id = "empty-plan-second", parent = "empty-plan"), today))
        val parent = r.files.single { !it.isNew }.text
        assertTrue(parent.contains("## Plan\n\n- [ ] `empty-plan-second`: Second\n\n## Work Log"), parent)
        write(fs, r)
        val codes = Validator(Repository(root, fs)).validate().map { it.code }
        assertEquals(listOf("parent-plan-missing-child"), codes) // only the pre-existing `child`
    }

    @Test
    fun usesTheProjectTemplate() {
        val template = ticket("the-ticket-id").replace("Why.", "<!-- What and why -->") + "\n## Notes\n\nProject section.\n"
        val (_, repo) = repo(template = template)
        val r = assertIs<NewTicketResult.Ready>(NewTicket.prepare(repo, NewTicketRequest("Herd photos"), today))
        val text = r.files.single().text
        assertTrue(text.contains("## Notes\n\nProject section.") && text.contains("<!-- What and why -->"), text)
        assertTrue(text.contains("id: herd-photos\n"))
    }

    @Test
    fun templatePerType() {
        val (fs, repo) = repo()
        fun created(type: TicketType) = assertIs<NewTicketResult.Ready>(
            NewTicket.prepare(repo, NewTicketRequest("Save crashes", type = type, objective = "Saving a movement crashes."), today),
        ).files.single()

        // Built-in bug template: the objective replaces the lead text, the subsections stay.
        val bug = created(TicketType.BUG)
        assertTrue(bug.text.contains("## Objective\n\nSaving a movement crashes.\n\n### Steps to reproduce\n"), bug.text)
        assertTrue(created(TicketType.RESEARCH).text.contains("## Learnings\n"))
        fs.write(bug.path) { writeUtf8(bug.text) }
        assertEquals(emptyList(), Validator(Repository(root, fs)).validate(listOf(bug.path)).map { it.toString() })
        fs.delete(bug.path)

        // The project's _TEMPLATE.md wins over built-in per-type ones; _TEMPLATE.<type>.md over both.
        fs.write(root / "tickets" / "_TEMPLATE.md") { writeUtf8(ticket("the-ticket-id").replace("Why.", "Project default.")) }
        assertEquals(root / "tickets" / "_TEMPLATE.md", NewTicket.template(Repository(root, fs), TicketType.BUG).first)
        fs.write(root / "tickets" / "_TEMPLATE.bug.md") { writeUtf8(ticket("the-ticket-id").replace("Why.", "Project bug.")) }
        val repo2 = Repository(root, fs)
        assertEquals(root / "tickets" / "_TEMPLATE.bug.md", NewTicket.template(repo2, TicketType.BUG).first)
        assertEquals(root / "tickets" / "_TEMPLATE.md", NewTicket.template(repo2, TicketType.FEATURE).first)
        assertEquals(emptyList(), repo2.tickets.filter { it.fileId.startsWith("_") }, "templates are not tickets")
    }

    @Test
    fun areaWithSeveralComponents() {
        val two = "  app:\n    external: true\n  server:\n    external: true\n"
        val (_, repo) = repo(components = two)
        assertTrue((NewTicket.prepare(repo, NewTicketRequest("X thing"), today) as NewTicketResult.Refused).reason.contains("area"))
        assertTrue((NewTicket.prepare(repo, NewTicketRequest("X thing", area = listOf("web")), today) as NewTicketResult.Refused).reason.contains("'web'"))
        val r = assertIs<NewTicketResult.Ready>(NewTicket.prepare(repo, NewTicketRequest("X thing", area = listOf("app", "server")), today))
        assertTrue(r.files.single().text.contains("area: [app, server]\n"))
    }

    @Test
    fun refusals() {
        val (_, repo) = repo()
        fun reason(request: NewTicketRequest) = assertIs<NewTicketResult.Refused>(NewTicket.prepare(repo, request, today)).reason
        assertTrue(reason(NewTicketRequest("Herd locations")).contains("already exists"))
        assertTrue(reason(NewTicketRequest("X", id = "Bad--id")).contains("not a valid id"))
        assertTrue(reason(NewTicketRequest("X", parent = "nope")).contains("no ticket 'nope'"))
        assertTrue(reason(NewTicketRequest("X", parent = "child")).contains("has a parent"))
        assertTrue(reason(NewTicketRequest("X", parent = "study")).contains("research"))
        assertTrue(reason(NewTicketRequest("!!!")).contains("give one"))
        assertTrue(reason(NewTicketRequest("two\nlines")).contains("one non-empty line"))
    }
}

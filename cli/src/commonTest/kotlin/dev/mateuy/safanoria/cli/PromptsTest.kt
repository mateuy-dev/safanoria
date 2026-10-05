package dev.mateuy.safanoria.cli

import com.github.ajalt.clikt.testing.test
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/** Answers in order, and records each question with the values it offered (the first one is the default). */
class ScriptedPrompts(vararg answers: Any) : Prompts {
    private val answers = answers.toMutableList()
    val asked = mutableListOf<Pair<String, List<String>>>()

    private fun next(question: String, offered: List<String> = emptyList()): Any {
        asked += question to offered
        check(answers.isNotEmpty()) { "unexpected question: $question" }
        return answers.removeAt(0)
    }

    override fun choose(title: String, choices: List<Choice>, default: String?): String {
        val answer = next(title, choices.map { it.value }) as String
        check(choices.any { it.value == answer }) { "'$answer' isn't offered for $title: ${choices.map { it.value }}" }
        return answer
    }

    @Suppress("UNCHECKED_CAST")
    override fun chooseMany(title: String, choices: List<Choice>, selected: List<String>): List<String> =
        next(title, choices.map { it.value }) as List<String>

    override fun text(prompt: String, default: String?): String = (next(prompt) as String).ifEmpty { default.orEmpty() }

    override fun confirm(prompt: String, default: Boolean): Boolean = next(prompt) as Boolean

    fun questions(): List<String> = asked.map { it.first }
}

class PromptsTest {
    @Test
    fun choiceLinesPutDescriptionsInAColumnBesideTheLabels() {
        val choices = listOf(Choice("new", description = "Create a ticket"), Choice("validate", description = "Check the tickets"), Choice("list"))
        assertEquals(
            listOf("new       <Create a ticket>", "validate  <Check the tickets>", "list"),
            choiceLines(choices, 80) { "<$it>" },
        )
        // A description that doesn't fit is cut, not wrapped; with no room at all it is left out.
        assertEquals(listOf("new       Creat…", "validate  Check…", "list"), choiceLines(choices, 16) { it })
        assertEquals(listOf("new", "validate", "list"), choiceLines(choices, 10) { it })
    }

    private fun run(repo: GitRepo, prompts: Prompts?, vararg args: String) =
        cli(prompts).test(listOf("--root", repo.root.toString()) + args.toList())

    @Test
    fun withoutATerminalNothingIsAsked() {
        val repo = GitRepo.scenario("prompts-none")
        val bare = run(repo, null)
        assertEquals(0, bare.statusCode, bare.output) // the help, as before prompts
        assertTrue("Usage:" in bare.output, bare.output)
        for (args in listOf(listOf("start"), listOf("finish"), listOf("release"), listOf("new"))) {
            val r = run(repo, null, *args.toTypedArray())
            assertEquals(1, r.statusCode, "${args}: ${r.output}")
            assertTrue("missing argument" in r.stderr, "${args}: ${r.stderr}")
        }
        assertEquals("main", repo.git("branch", "--show-current").trim())
    }

    @Test
    fun theRootCommandAsksWhichAction() {
        val repo = GitRepo.scenario("prompts-root")
        val prompts = ScriptedPrompts("list")
        val r = run(repo, prompts)
        assertEquals(0, r.statusCode, r.output)
        assertTrue("alpha" in r.stdout && "beta" in r.stdout, r.stdout)
        val offered = prompts.asked.single().second
        assertTrue("new" in offered && "start" in offered && "init" !in offered, offered.toString())
    }

    @Test
    fun startOffersBacklogAndReadyTickets() {
        val repo = GitRepo.scenario("prompts-start")
        val prompts = ScriptedPrompts("alpha")
        val r = run(repo, prompts, "start", "--date", "2026-10-03")
        assertEquals(0, r.statusCode, r.output)
        assertEquals(listOf("alpha", "stray"), prompts.asked.single().second) // beta is in progress
        assertEquals("alpha", repo.git("branch", "--show-current").trim())
    }

    @Test
    fun finishOffersInProgressTickets() {
        val repo = GitRepo.scenario("prompts-finish")
        val prompts = ScriptedPrompts("beta")
        val r = run(repo, prompts, "finish", "--date", "2026-10-03")
        assertEquals(0, r.statusCode, r.output)
        assertEquals(listOf("beta"), prompts.asked.single().second)
        assertTrue("status: review" in repo.git("show", "beta:tickets/beta.md"))
    }

    @Test
    fun newAsksTitleTypeAndId() {
        val repo = GitRepo.scenario("prompts-new")
        val prompts = ScriptedPrompts("Herd photos", "bug", false, "")
        val r = run(repo, prompts, "new", "--date", "2026-10-03")
        assertEquals(0, r.statusCode, r.output)
        assertEquals(listOf("Title", "Type", "Set priority, size, parent or objective?", "Id, also the branch name"), prompts.questions())
        val text = repo.read("tickets/herd-photos.md")
        assertTrue("type: bug\n" in text && "title: Herd photos\n" in text, text)
    }

    @Test
    fun newAsksTheRestWhenWanted() {
        val repo = GitRepo.scenario("prompts-new-more")
        val prompts = ScriptedPrompts("Herd photos", true, "high", "M", "alpha", "Photos of the herd.", "photos")
        val r = run(repo, prompts, "new", "--type", "feature", "--date", "2026-10-03")
        assertEquals(0, r.statusCode, r.output)
        assertFalse("Type" in prompts.questions(), "given as a flag: ${prompts.questions()}")
        val text = repo.read("tickets/photos.md")
        assertTrue("priority: high\n" in text && "size: M\n" in text && "parent: alpha\n" in text, text)
        assertTrue("Photos of the herd." in text, text)
    }

    @Test
    fun newOffersTheDeclaredTags() {
        val repo = GitRepo.scenario("prompts-new-tags")
        repo.write("safanoria.yaml", repo.read("safanoria.yaml") + "tags:\n  registry: Official registry integration\n  offline: Working without a connection\n")
        repo.commit("tags")
        val prompts = ScriptedPrompts("Herd photos", "feature", true, "medium", "S", "", listOf("offline"), "", "")
        val r = run(repo, prompts, "new", "--date", "2026-10-03")
        assertEquals(0, r.statusCode, r.output)
        assertTrue("Set priority, size, parent, tags or objective?" in prompts.questions(), prompts.questions().toString())
        assertEquals(listOf("registry", "offline"), prompts.asked.first { it.first.startsWith("Tags") }.second)
        assertTrue("tags: [offline]\n" in repo.read("tickets/herd-photos.md"), repo.read("tickets/herd-photos.md"))
    }

    @Test
    fun newOnAnotherBranchAsksWhereItGoes() {
        val repo = GitRepo.scenario("prompts-new-branch")
        repo.checkout("feature")
        val prompts = ScriptedPrompts("Herd photos", "feature", false, "main", "")
        val r = run(repo, prompts, "new", "--date", "2026-10-03")
        assertEquals(0, r.statusCode, r.output)
        assertTrue("herd-photos" in repo.git("show", "--name-only", "main"), "committed on main")
        assertEquals("feature", repo.git("branch", "--show-current").trim())
    }

    @Test
    fun newWithATitleAsksOnlyForAMissingArea() {
        val repo = GitRepo.scenario("prompts-new-area")
        repo.write("safanoria.yaml", "safanoria: 1\ncomponents:\n  app:\n    external: true\n  server:\n    external: true\n")
        repo.commit("two components")
        val prompts = ScriptedPrompts(listOf("server"))
        val r = run(repo, prompts, "new", "Herd photos", "--date", "2026-10-03")
        assertEquals(0, r.statusCode, r.output)
        assertEquals(listOf("app", "server"), prompts.asked.single().second)
        assertTrue("area: [server]" in repo.read("tickets/herd-photos.md"), repo.read("tickets/herd-photos.md"))
    }
}

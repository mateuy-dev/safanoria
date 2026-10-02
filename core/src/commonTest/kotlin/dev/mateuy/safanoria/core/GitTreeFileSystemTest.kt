package dev.mateuy.safanoria.core

import okio.IOException
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class GitTreeFileSystemTest {
    @Test
    fun aBranchOpensAsARepository() {
        val f = GitFixture("tree-fs")
        f.write(CONFIG_FILE, GitFixture.CONFIG)
        f.write("tickets/alpha.md", GitFixture.ticket("alpha"))
        f.write("tickets/_TEMPLATE.md", "template\n")
        f.write("tickets/attachments/alpha/shot.png", "12345")
        f.commit("init")
        f.checkout("feature", create = true)
        f.write("tickets/beta.md", GitFixture.ticket("beta", related = listOf("alpha")))
        f.commit("beta")
        f.checkout("main")
        f.write("tickets/alpha.md", GitFixture.ticket("alpha", status = "ready")) // uncommitted: not in the tree

        val git = f.repository.git
        val blobs = BlobCache(git)
        val fs = GitTreeFileSystem(git, "feature", f.root, blobs)
        val repo = Repository(f.root, fs)

        assertEquals(listOf("app"), repo.config.components.keys.toList())
        assertEquals(listOf("alpha", "beta"), repo.tickets.map { it.fileId })
        assertEquals("backlog", repo.ticket("alpha")!!.frontmatter!!.status!!.text)
        assertEquals(emptyList(), Validator(repo).validate())

        assertTrue(fs.metadata(f.root / "tickets").isDirectory)
        assertEquals(5L, fs.metadata(f.root / "tickets" / "attachments" / "alpha" / "shot.png").size)
        assertFalse(fs.exists(f.root / "tickets" / "gamma.md"))
        assertFalse(fs.exists(f.root.parent!! / "elsewhere"))
        assertNull(fs.listOrNull(f.root / "nothing"))
        assertEquals(f.root / "tickets" / "beta.md", fs.canonicalize(f.root / "tickets" / ".." / "tickets" / "beta.md"))
        assertFailsWith<IOException> { fs.write(f.root / "tickets" / "x.md") { writeUtf8("x") } }

        // Same blobs on another branch aren't read again.
        val before = blobs.reads
        Repository(f.root, GitTreeFileSystem(git, "main", f.root, blobs)).tickets.forEach { it.text }
        assertEquals(before, blobs.reads)
    }
}

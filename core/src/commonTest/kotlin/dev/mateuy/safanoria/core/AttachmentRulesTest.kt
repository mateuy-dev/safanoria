package dev.mateuy.safanoria.core

import okio.Path.Companion.toPath
import okio.fakefilesystem.FakeFileSystem
import kotlin.test.Test
import kotlin.test.assertEquals

class AttachmentRulesTest {
    @Test
    fun largeFileIsAWarningCausedByItsTicket() {
        val fs = FakeFileSystem()
        val dir = "/repo/tickets".toPath()
        fs.createDirectories(dir / "attachments" / "crash-on-save")
        fs.write("/repo/safanoria.yaml".toPath()) { writeUtf8("safanoria: 1\ncomponents:\n  app:\n    external: true\n") }
        fs.write(dir / "crash-on-save.md") {
            writeUtf8("---\nid: crash-on-save\ntype: bug\ntitle: Crash\nstatus: backlog\npriority: medium\nsize: S\n" +
                "created: 2026-10-01\nupdated: 2026-10-01\n---\n\n## Objective\n\n![v](attachments/crash-on-save/video.mp4)\n\n" +
                "## Acceptance Criteria\n\n## Plan\n\n## Work Log\n")
        }
        fs.write(dir / "attachments" / "crash-on-save" / "video.mp4") { write(ByteArray(1024 * 1024 + 1)) }
        fs.write(dir / "attachments" / "crash-on-save" / "small.png") { write(ByteArray(1024 * 1024)) }

        val validator = Validator(Repository("/repo".toPath(), fs))
        val all = validator.validate()
        assertEquals(listOf("attachment-large"), all.map { it.code })
        assertEquals(Severity.WARNING, all.single().severity)
        assertEquals(dir / "attachments" / "crash-on-save" / "video.mp4", all.single().file)
        assertEquals(all, validator.validate(listOf(dir / "crash-on-save.md")), "reported when its ticket is given")
    }
}

import java.nio.file.Files
import org.jetbrains.kotlin.gradle.dsl.KotlinMultiplatformExtension
import org.jetbrains.kotlin.gradle.plugin.mpp.KotlinNativeTarget
import org.jetbrains.kotlin.konan.target.KonanTarget

plugins {
    alias(libs.plugins.kotlin.multiplatform) apply false
    alias(libs.plugins.compose.multiplatform) apply false
    alias(libs.plugins.compose.compiler) apply false
}

// json-schema-validator -> com.doist.x:normalize links -lunistring on linuxX64, and the
// Kotlin/Native sysroot doesn't have it. Point the linker at the system library through a
// symlink; the binary then needs libunistring.so.5 at runtime (Ubuntu 24.04+).
// Linux binaries are therefore built on Linux. See tickets/v1-tooling-native-spike.md.
// Tests read this repository's files (schema/examples, tickets, fixtures), and optionally other
// local repositories' tickets (SAFANORIA_EXTRA_REPOS, paths separated by ':' or ';').
val testEnvironment = buildMap {
    put("SAFANORIA_REPO_ROOT", rootDir.absolutePath)
    System.getenv("SAFANORIA_EXTRA_REPOS")?.let { put("SAFANORIA_EXTRA_REPOS", it) }
}

subprojects {
    // Tests create git repositories in build/test-repos. Git marks its object files read-only,
    // and on Windows a test can't delete those, so each test task starts without the
    // repositories the previous one left (jvmTest, then mingwX64Test).
    val testRepos = layout.buildDirectory.dir("test-repos")
    val deleteTestRepos = Action<Task> {
        val dir = testRepos.get().asFile
        if (dir.exists()) dir.walkBottomUp().forEach {
            it.setWritable(true)
            if (!it.delete()) throw GradleException("can't delete $it")
        }
    }

    // Environment variables aren't task inputs by default: without inputs.property, changing
    // SAFANORIA_EXTRA_REPOS would reuse a cached test result.
    tasks.withType<Test>().configureEach {
        testEnvironment.forEach { (k, v) -> environment(k, v) }
        inputs.property("testEnvironment", testEnvironment)
        doFirst(deleteTestRepos)
    }
    tasks.withType<org.jetbrains.kotlin.gradle.targets.native.tasks.KotlinNativeTest>().configureEach {
        testEnvironment.forEach { (k, v) -> environment(k, v) }
        inputs.property("testEnvironment", testEnvironment)
        doFirst(deleteTestRepos)
    }

    plugins.withId("org.jetbrains.kotlin.multiplatform") {
        val libDir = layout.buildDirectory.dir("native-libs")
        val linkUnistring = tasks.register("linkUnistring") {
            description = "Symlinks the system libunistring.so.5 for the linuxX64 linker."
            outputs.dir(libDir)
            doLast {
                val lib = listOf("/usr/lib/x86_64-linux-gnu", "/usr/lib64", "/usr/lib")
                    .map { File(it, "libunistring.so.5") }
                    .firstOrNull { it.exists() }
                    ?: throw GradleException(
                        "libunistring.so.5 not found: install libunistring (Ubuntu 24.04+: libunistring5). " +
                            "Linux binaries must be built on Linux."
                    )
                val link = libDir.get().file("libunistring.so").asFile.toPath()
                Files.createDirectories(link.parent)
                Files.deleteIfExists(link)
                Files.createSymbolicLink(link, lib.toPath())
            }
        }
        val linuxHost = System.getProperty("os.name").startsWith("Linux")
        extensions.configure<KotlinMultiplatformExtension> {
            targets.withType<KotlinNativeTarget>()
                .matching { it.konanTarget == KonanTarget.LINUX_X64 }
                .configureEach {
                    binaries.all {
                        if (linuxHost) {
                            linkerOpts("-L${libDir.get().asFile}")
                            linkTaskProvider.configure { dependsOn(linkUnistring) }
                        } else {
                            // Kotlin/Native could cross-link, but not without the system library.
                            linkTaskProvider.configure { enabled = false }
                        }
                    }
                }
        }
    }
}

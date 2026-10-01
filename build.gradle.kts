import java.nio.file.Files
import org.jetbrains.kotlin.gradle.dsl.KotlinMultiplatformExtension
import org.jetbrains.kotlin.gradle.plugin.mpp.KotlinNativeTarget
import org.jetbrains.kotlin.konan.target.KonanTarget

plugins {
    alias(libs.plugins.kotlin.multiplatform) apply false
}

// json-schema-validator -> com.doist.x:normalize links -lunistring on linuxX64, and the
// Kotlin/Native sysroot doesn't have it. Point the linker at the system library through a
// symlink; the binary then needs libunistring.so.5 at runtime (Ubuntu 24.04+).
// Linux binaries are therefore built on Linux. See tickets/v1-tooling-native-spike.md.
subprojects {
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
        extensions.configure<KotlinMultiplatformExtension> {
            targets.withType<KotlinNativeTarget>()
                .matching { it.konanTarget == KonanTarget.LINUX_X64 }
                .configureEach {
                    binaries.all {
                        linkerOpts("-L${libDir.get().asFile}")
                        linkTaskProvider.configure { dependsOn(linkUnistring) }
                    }
                }
        }
    }
}

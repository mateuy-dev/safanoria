plugins {
    alias(libs.plugins.kotlin.multiplatform)
    alias(libs.plugins.compose.multiplatform)
    alias(libs.plugins.compose.compiler)
}

// The desktop app (Compose Desktop, JVM only): the `safanoria` command. Run it from a checkout
// with `./gradlew :gui:run --args=<path>` (default: the directory gradle runs in).
kotlin {
    jvmToolchain(21)
    jvm()

    sourceSets {
        // The app's icons are the repository's (design/), not a copy.
        jvmMain { resources.srcDir(rootProject.layout.projectDirectory.dir("design")) }
        jvmMain.dependencies {
            implementation(project(":core"))
            implementation(compose.desktop.currentOs)
            implementation(libs.compose.runtime)
            implementation(libs.compose.foundation)
            implementation(libs.compose.ui)
            implementation(libs.compose.material3)
            implementation(libs.compose.resources) // decodes the SVG icons
            implementation(libs.navigation3.ui)
            implementation(libs.lifecycle.viewmodel.compose)
            implementation(libs.lifecycle.viewmodel.navigation3)
            implementation(libs.lifecycle.runtime.compose)
            implementation(libs.markdown.renderer.m3)
            // Dispatchers.Main on the JVM, which viewModelScope uses.
            implementation(libs.kotlinx.coroutines.swing)
        }
        jvmTest.dependencies {
            implementation(kotlin("test"))
        }
    }
}

compose.desktop {
    application {
        mainClass = "dev.mateuy.safanoria.gui.MainKt"
        // Same rule as the CLI (core/build.gradle.kts): only release builds report the plain version.
        val plain = project.version.toString()
        jvmArgs += "-Dsafanoria.version=" + if (providers.gradleProperty("release").isPresent) plain else "$plain-dev"

        // `createDistributable`: the app with its own Java runtime, in
        // build/compose/binaries/main/app/. The release archives and `make install` are that
        // directory; the install scripts put a `safanoria` launcher for it on the PATH.
        nativeDistributions {
            packageName = "safanoria"
            packageVersion = plain
            modules("java.instrument", "jdk.unsupported")
            // A command typed in a terminal: its messages (no project here, usage) must show there.
            windows { console = true }
            // macOS refuses a version whose first number is 0.
            macOS { packageVersion = plain.replace(Regex("^0\\."), "1.") }
        }
    }
}

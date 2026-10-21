plugins {
    alias(libs.plugins.kotlin.multiplatform)
}

// The terminal app: the board, ticket and versions screens of the desktop app (gui) in a terminal,
// with the keyboard and the mouse. `safanoria-cli tui` runs it, so it ships in the native CLI.
kotlin {
    jvmToolchain(21)
    jvm()
    linuxX64()
    mingwX64()
    macosArm64()

    applyDefaultHierarchyTemplate {
        common {
            group("posix") {
                withLinux()
                withApple()
            }
        }
    }

    sourceSets {
        commonMain.dependencies {
            api(project(":views"))
            api(libs.mordant)
            implementation(libs.mordant.markdown)
        }
        commonTest.dependencies {
            implementation(kotlin("test"))
        }
    }
}

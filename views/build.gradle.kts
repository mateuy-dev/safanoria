plugins {
    alias(libs.plugins.kotlin.multiplatform)
}

// What the screens of the desktop app (gui) and the terminal app (tui) show, and the actions
// on tickets they offer, without any UI: both apps compute the same board, ticket and versions.
kotlin {
    jvmToolchain(21)
    jvm()
    linuxX64()
    mingwX64()
    macosArm64()

    sourceSets {
        commonMain.dependencies {
            api(project(":core"))
            api(libs.kotlinx.coroutines.core)
            implementation(libs.kotlinx.datetime)
        }
        commonTest.dependencies {
            implementation(kotlin("test"))
        }
    }
}

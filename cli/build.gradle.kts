plugins {
    alias(libs.plugins.kotlin.multiplatform)
}

kotlin {
    jvmToolchain(21)
    jvm {
        // JVM build: tests, and a fallback way to run the CLI. Releases use the native binaries.
        @OptIn(org.jetbrains.kotlin.gradle.ExperimentalKotlinGradlePluginApi::class)
        mainRun { mainClass.set("dev.mateuy.safanoria.cli.MainKt") }
    }
    listOf(linuxX64(), mingwX64(), macosArm64()).forEach { target ->
        target.binaries.executable {
            baseName = "safanoria"
            entryPoint = "dev.mateuy.safanoria.cli.main"
        }
    }

    sourceSets {
        commonMain.dependencies {
            implementation(project(":core"))
            implementation(libs.clikt)
        }
        commonTest.dependencies {
            implementation(kotlin("test"))
        }
    }
}

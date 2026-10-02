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
        // The compiler caches (debug builds) link clikt and clikt-mordant with a duplicate symbol
        // (Context.selfAndAncestors), which breaks the test binary. Release builds use no caches.
        // Tied to the Kotlin version: on an upgrade, check whether it's still needed.
        target.binaries.configureEach {
            @OptIn(org.jetbrains.kotlin.gradle.plugin.mpp.KotlinNativeCacheApi::class)
            disableNativeCache(
                org.jetbrains.kotlin.gradle.plugin.mpp.DisableCacheInKotlinVersion.`2_4_20`,
                "clikt and clikt-mordant caches define Context.selfAndAncestors twice",
            )
        }
    }

    sourceSets {
        commonMain.dependencies {
            implementation(project(":core"))
            implementation(libs.clikt)
            implementation(libs.kotlinx.serialization.json)
            implementation(libs.kotlinx.datetime)
        }
        commonTest.dependencies {
            implementation(kotlin("test"))
        }
    }
}

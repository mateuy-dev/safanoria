plugins {
    kotlin("multiplatform") version "2.4.20"
    kotlin("plugin.serialization") version "2.4.20"
}

kotlin {
    jvm {
        @OptIn(org.jetbrains.kotlin.gradle.ExperimentalKotlinGradlePluginApi::class)
        mainRun { mainClass.set("MainKt") }
    }
    listOf(linuxX64(), mingwX64(), macosArm64()).forEach { target ->
        target.binaries.executable {
            baseName = "safanoria"
            entryPoint = "main"
        }
    }

    sourceSets {
        commonMain.dependencies {
            implementation("com.charleskorn.kaml:kaml:0.104.0")
            implementation("com.squareup.okio:okio:3.18.2")
            implementation("com.github.ajalt.clikt:clikt:5.1.0")
        }
    }
}

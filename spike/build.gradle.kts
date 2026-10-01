plugins {
    kotlin("multiplatform") version "2.4.20"
}

kotlin {
    jvm()
    listOf(linuxX64(), mingwX64(), macosArm64()).forEach { target ->
        target.binaries.executable {
            baseName = "safanoria"
            entryPoint = "main"
        }
    }
}

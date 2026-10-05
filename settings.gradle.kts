rootProject.name = "safanoria"

pluginManagement {
    repositories {
        gradlePluginPortal()
        mavenCentral()
    }
}

dependencyResolutionManagement {
    repositories {
        mavenCentral()
        google() // androidx artifacts behind Compose Multiplatform (gui)
    }
}

include(":core", ":cli", ":gui")

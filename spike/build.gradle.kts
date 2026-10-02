plugins {
    alias(libs.plugins.kotlin.multiplatform)
    id("com.android.kotlin.multiplatform.library") version "8.13.2"
}

kotlin {
    jvmToolchain(21)
    androidLibrary {
        namespace = "dev.mateuy.safanoria.core"
        compileSdk = 36
        minSdk = 26
    }
    sourceSets {
        commonMain {
            kotlin.srcDir("../core/src/commonMain/kotlin")
            kotlin.srcDir("../core/build/generated/embedded")
            dependencies {
                api(libs.okio)
                implementation(libs.kaml)
                implementation(libs.json.schema.validator)
                implementation(libs.kotlinx.serialization.json)
            }
        }
        androidMain { kotlin.srcDir("../core/src/jvmMain/kotlin") }
    }
}

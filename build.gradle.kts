// Top-level build file. Plugin versions are declared here once with
// `apply false`, then applied in the module(s) that need them.
plugins {
    id("com.android.application") version "9.2.1" apply false
    id("org.jetbrains.kotlin.android") version "2.2.10" apply false
    // Kotlin 2.0+ moved the Compose compiler into a dedicated Gradle plugin.
    id("org.jetbrains.kotlin.plugin.compose") version "2.2.10" apply false
}

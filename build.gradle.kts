// Top-level build file where you can add configuration options common to all sub-projects/modules.
plugins {
    id("com.android.application") version "8.13.2" apply false
    id("com.android.library") version "8.13.2" apply false
    id("org.jetbrains.kotlin.android") version "2.3.10" apply false
    id("org.jetbrains.kotlin.plugin.compose") version "2.0.0" apply false // Use 2.0.0 or match Kotlin version if possible, but 2.3.10 sounds like AGP version? No, AGP is 8.13.2. Let's try matching or using a known good version for 2.0.
}

// Top-level build file where you can add configuration options common to all sub-projects/modules.
plugins {
    id("com.android.application") version "8.13.1" apply false
    id("org.jetbrains.kotlin.android") version "1.9.0" apply false

    // Updated Google Services plugin as required by Firebase Setup
    id("com.google.gms.google-services") version "4.4.4" apply false

    // Crashlytics (keep your version)
    id("com.google.firebase.crashlytics") version "2.9.9" apply false
}

tasks.register("clean", Delete::class) {
    delete(rootProject.buildDir)
}


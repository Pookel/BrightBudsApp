plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")

    // Google Services plugin (required for google-services.json)
    id("com.google.gms.google-services")

    // Crashlytics
    id("com.google.firebase.crashlytics")
}

android {
    namespace = "com.example.brightbuds_app"
    compileSdk = 34

    defaultConfig {
        applicationId = "com.example.brightbuds_app"
        minSdk = 23
        targetSdk = 34
        versionCode = 1
        versionName = "1.0"

        buildConfigField(
            "String",
            "BRIGHTBUDS_KEY",
            "\"${project.findProperty("BRIGHTBUDS_KEY") ?: ""}\""
        )
    }

    /*
     * R8 IS DISABLED COMPLETELY TO FIX THE ERROR:
     * "An error occurred when parsing kotlin metadata"
     */
    buildTypes {
        debug {
            // Disable R8, shrinking, and obfuscation
            isMinifyEnabled = false
            isShrinkResources = false
        }

        release {
            // Disable R8, shrinking, and obfuscation
            isMinifyEnabled = false
            isShrinkResources = false

            // Keep your proguard file even though it's not used (safe)
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
    }

    buildFeatures {
        viewBinding = true
        buildConfig = true
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    kotlinOptions {
        jvmTarget = "17"
    }
}

dependencies {
    // Android Core
    implementation("androidx.appcompat:appcompat:1.7.0")
    implementation("com.google.android.material:material:1.12.0")
    implementation("androidx.constraintlayout:constraintlayout:2.2.0")

    // Firebase BoM
    implementation(platform("com.google.firebase:firebase-bom:34.6.0"))

    // Firebase Products
    implementation("com.google.firebase:firebase-auth")
    implementation("com.google.firebase:firebase-firestore")
    implementation("com.google.firebase:firebase-storage")
    implementation("com.google.firebase:firebase-crashlytics")
    implementation("com.google.firebase:firebase-analytics")
    implementation("com.google.firebase:firebase-config")

    // Play Services
    implementation("com.google.android.gms:play-services-auth:21.4.0")

    // Media3
    implementation("androidx.media3:media3-exoplayer:1.3.1")
    implementation("androidx.media3:media3-ui:1.3.1")

    // Image Loading and Charts
    implementation("com.github.bumptech.glide:glide:4.16.0")
    implementation("com.github.PhilJay:MPAndroidChart:v3.1.0")
    implementation("de.hdodenhof:circleimageview:3.1.0")

    // PDF generation
    implementation("com.itextpdf:itextpdf:5.5.13.3")

    // JSON parsing
    implementation("com.google.code.gson:gson:2.10.1")

    // Konfetti
    implementation("nl.dionsegijn:konfetti-xml:2.0.4")

    implementation("com.github.PhilJay:MPAndroidChart:v3.1.0")
    implementation("com.google.android.flexbox:flexbox:3.0.0")


    // Testing
    testImplementation("junit:junit:4.13.2")
    androidTestImplementation("androidx.test.ext:junit:1.2.1")
    androidTestImplementation("androidx.test.espresso:espresso-core:3.6.1")
}

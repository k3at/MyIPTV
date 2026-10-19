import org.jetbrains.kotlin.gradle.dsl.JvmTarget

// Release signing details live in keystore.properties, which is never committed.
// If the file is missing the release build falls back to the debug key so a clean
// checkout still builds (useful for CI and for anyone cloning the project).
val keystoreProps: Map<String, String> = rootProject.file("keystore.properties")
    .let { file -> if (file.exists()) file.readLines() else emptyList() }
    .mapNotNull { line ->
        val i = line.indexOf('=')
        if (i <= 0) null else line.substring(0, i).trim() to line.substring(i + 1).trim()
    }
    .toMap()
val hasUploadKey = keystoreProps.containsKey("storeFile")

plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("kotlin-parcelize")
}

android {
    namespace = "com.myiptv.app"
    compileSdk = 37

    defaultConfig {
        applicationId = "com.myiptv.app"
        minSdk = 23
        targetSdk = 37
        versionCode = 4
        versionName = "2.0.0"
        vectorDrawables.useSupportLibrary = true
    }

    signingConfigs {
        if (hasUploadKey) {
            create("release") {
                storeFile = rootProject.file(keystoreProps["storeFile"]!!)
                storePassword = keystoreProps["storePassword"]
                keyAlias = keystoreProps["keyAlias"]
                keyPassword = keystoreProps["keyPassword"]
            }
        }
    }

    buildTypes {
        debug {
            isMinifyEnabled = false
        }
        release {
            // Real upload key when keystore.properties is present (see README),
            // otherwise the debug key so a fresh clone still builds.
            // For Play Store releases the upload key is mandatory.
            signingConfig = if (hasUploadKey) {
                signingConfigs.getByName("release")
            } else {
                signingConfigs.getByName("debug")
            }
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    buildFeatures {
        viewBinding = true
    }

    packaging {
        resources.excludes += "/META-INF/{AL2.0,LGPL2.1}"
    }
}

kotlin {
    compilerOptions {
        jvmTarget.set(JvmTarget.JVM_17)
    }
}

dependencies {
    implementation("androidx.core:core-ktx:1.19.1")
    implementation("androidx.appcompat:appcompat:1.8.0")
    implementation("com.google.android.material:material:1.14.0")
    implementation("androidx.constraintlayout:constraintlayout:2.2.2")
    implementation("androidx.recyclerview:recyclerview:1.4.0")
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.9.4")
    implementation("androidx.lifecycle:lifecycle-viewmodel-ktx:2.9.4")

    // Playback
    implementation("androidx.media3:media3-exoplayer:1.11.1")
    implementation("androidx.media3:media3-exoplayer-hls:1.11.1")
    implementation("androidx.media3:media3-ui:1.11.1")

    // Networking (playlist + Xtream API)
    implementation("com.squareup.okhttp3:okhttp:4.12.0")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.11.0")

    // Channel logos
    implementation("com.github.bumptech.glide:glide:4.16.0")
}

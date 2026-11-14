plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

android {
    namespace = "com.mitesh.livetranslator"
    compileSdk = 34

    defaultConfig {
        applicationId = "com.mitesh.livetranslator"
        minSdk = 26
        targetSdk = 34
        versionCode = 5
        versionName = "1.4.0"
    }

    // Fixed key so every CI build installs over the previous one.
    signingConfigs {
        getByName("debug") {
            storeFile = file("keystore/bayan.jks")
            storePassword = "bayan-debug"
            keyAlias = "bayan"
            keyPassword = "bayan-debug"
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = false
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions {
        jvmTarget = "17"
    }
    buildFeatures {
        viewBinding = true
    }
}

dependencies {
    implementation("androidx.core:core-ktx:1.13.1")
    implementation("androidx.appcompat:appcompat:1.7.0")
    implementation("com.google.android.material:material:1.12.0")
    implementation("com.microsoft.cognitiveservices.speech:client-sdk:1.40.0")
}

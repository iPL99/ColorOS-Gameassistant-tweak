plugins {
    id("com.android.application")
}

android {
    namespace = "dev.gyrokr.vectorhook"
    compileSdk = 36

    defaultConfig {
        applicationId = "dev.gyrokr.vectorhook"
        minSdk = 26
        targetSdk = 35
        versionCode = 1
        versionName = "1.0.0"
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    sourceSets["main"].resources.srcDir("src/main/resources")
}

dependencies {
    // The framework provides this API at runtime; do not package it into the APK.
    compileOnly("io.github.libxposed:api:102.0.0")
}

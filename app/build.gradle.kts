plugins {
    id("com.android.application")
}

android {
    namespace = "io.betnet.smssender"
    compileSdk = 35

    defaultConfig {
        applicationId = "io.betnet.smssender"
        minSdk = 26
        targetSdk = 35
        versionCode = 170
        versionName = "1.7.0"
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
}

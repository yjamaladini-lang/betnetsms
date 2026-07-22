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
        versionCode = 178
        versionName = "1.7.8"
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


dependencies {
    implementation("androidx.appcompat:appcompat:1.7.0")
}

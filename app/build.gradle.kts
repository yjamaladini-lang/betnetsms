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
        versionCode = 181
        versionName = "1.8.1"
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



configurations.configureEach {
    resolutionStrategy {
        force(
            "org.jetbrains.kotlin:kotlin-stdlib:1.8.22",
            "org.jetbrains.kotlin:kotlin-stdlib-common:1.8.22",
            "org.jetbrains.kotlin:kotlin-stdlib-jdk7:1.8.22",
            "org.jetbrains.kotlin:kotlin-stdlib-jdk8:1.8.22"
        )
    }
}

dependencies {
    implementation(platform("org.jetbrains.kotlin:kotlin-bom:1.8.22"))

    implementation("androidx.appcompat:appcompat:1.7.0") {
        exclude(
            group = "org.jetbrains.kotlin",
            module = "kotlin-stdlib-jdk7"
        )
        exclude(
            group = "org.jetbrains.kotlin",
            module = "kotlin-stdlib-jdk8"
        )
    }

    implementation("org.jetbrains.kotlin:kotlin-stdlib:1.8.22")
}

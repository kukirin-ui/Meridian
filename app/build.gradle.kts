plugins {
    id("com.android.application")
}

android {
    namespace = "com.meridian.shell"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.meridian.shell"
        minSdk = 26
        targetSdk = 35
        versionCode = 47
        versionName = "2.3.4"
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
}

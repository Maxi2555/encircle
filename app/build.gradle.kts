plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
}

android {
    namespace = "de.volcanoproject.encircle"
    compileSdk {
        version = release(37) {
            minorApiLevel = 1
        }
    }

    defaultConfig {
        applicationId = "de.volcanoproject.encircle"
        minSdk = 29
        targetSdk = 37
        versionCode = 1
        versionName = "0.1.0"
    }

    // Release signing comes from the environment (see docs/RELEASING.md). Without it, release
    // builds fall back to the local debug key so they can still be installed for testing.
    val keystorePath = System.getenv("ENCIRCLE_KEYSTORE_FILE")
    val releaseSigning = if (!keystorePath.isNullOrBlank() && file(keystorePath).exists()) {
        signingConfigs.create("release") {
            storeFile = file(keystorePath)
            storePassword = System.getenv("ENCIRCLE_KEYSTORE_PASSWORD")
            keyAlias = System.getenv("ENCIRCLE_KEY_ALIAS")
            keyPassword = System.getenv("ENCIRCLE_KEY_PASSWORD")
        }
    } else {
        null
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro",
            )
            signingConfig = releaseSigning ?: signingConfigs.getByName("debug")
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    buildFeatures {
        compose = true
    }

    // Per-ABI APKs for GitHub releases. Google Play builds its own split APKs from the app bundle,
    // and AGP cannot produce both in one invocation (issuetracker.google.com/402800800).
    val buildingBundle = gradle.startParameter.taskNames.any { it.contains("bundle", ignoreCase = true) }
    splits {
        abi {
            isEnable = !buildingBundle
            reset()
            include("arm64-v8a", "armeabi-v7a", "x86_64")
            isUniversalApk = true
        }
    }

    packaging {
        resources.excludes += "/META-INF/{AL2.0,LGPL2.1}"
    }
}

dependencies {
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.lifecycle.viewmodel)
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.foundation)
    implementation(libs.androidx.compose.material3)
    implementation(libs.mlkit.text.recognition)
    implementation(libs.zxing.cpp)

    testImplementation(libs.junit)
}

plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
}

android {
    namespace = "com.smnexstudio.memora"
    compileSdk = 36

    defaultConfig {
        applicationId = "com.smnexstudio.memora"
        minSdk = 33
        targetSdk = 36
        // CI passes these from the release tag (see .github/workflows/release.yml).
        versionCode = (findProperty("versionCode") as String?)?.toInt() ?: 10000
        versionName = (findProperty("versionName") as String?) ?: "1.0.0"
    }

    signingConfigs {
        // A release key from the environment (CI secrets) when there is one; otherwise the
        // shared debug key, the same as the earlier Expo build so it installs as an update.
        // Replace the fallback with your own keystore before publishing.
        create("memora") {
            val keystore = System.getenv("MEMORA_KEYSTORE_FILE")?.takeIf { it.isNotBlank() && file(it).exists() }
            if (keystore != null) {
                storeFile = file(keystore)
                storePassword = System.getenv("MEMORA_KEYSTORE_PASSWORD")
                keyAlias = System.getenv("MEMORA_KEY_ALIAS")
                keyPassword = System.getenv("MEMORA_KEY_PASSWORD")
            } else {
                // A release built on GitHub must never fall back to the public debug key.
                if (System.getenv("CI") == "true") {
                    throw GradleException("MEMORA_KEYSTORE_FILE is not set: add the signing secrets before releasing.")
                }
                storeFile = file("debug.keystore")
                storePassword = "android"
                keyAlias = "androiddebugkey"
                keyPassword = "android"
            }
        }
    }

    buildTypes {
        debug {
            signingConfig = signingConfigs.getByName("memora")
        }
        release {
            // R8: shrinks and optimises the code and drops unused resources.
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
            signingConfig = signingConfigs.getByName("memora")
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    buildFeatures {
        compose = true
    }
}

kotlin {
    jvmToolchain(17)
}

dependencies {
    val composeBom = platform("androidx.compose:compose-bom:2025.09.00")
    implementation(composeBom)
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.foundation:foundation")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.activity:activity-compose:1.10.1")
    implementation("androidx.navigation:navigation-compose:2.9.3")
    implementation("androidx.lifecycle:lifecycle-runtime-compose:2.9.3")
    implementation("androidx.fragment:fragment-ktx:1.8.9")
    implementation("androidx.biometric:biometric:1.1.0")
    implementation("androidx.core:core-ktx:1.16.0")
    debugImplementation("androidx.compose.ui:ui-tooling")
    testImplementation("junit:junit:4.13.2")
}

plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
    // Type-safe navigation routes (ui/Route.kt).
    id("org.jetbrains.kotlin.plugin.serialization")
    // A software bill of materials for each release (see .github/workflows/release.yml).
    id("org.cyclonedx.bom")
}

// Local test builds of the release variant may use the public debug key. Never share the result.
val allowDebugSigning = findProperty("allowDebugSigning") == "true"

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
        manifestPlaceholders["appLabel"] = "Memora"
    }

    signingConfigs {
        // Debug builds use the shared debug key committed in the repo. That key is public, so
        // nothing signed with it may ever be published (see the release config below).
        getByName("debug") {
            storeFile = file("debug.keystore")
            storePassword = "android"
            keyAlias = "androiddebugkey"
            keyPassword = "android"
        }
        // The real release key, from the environment (CI secrets or a developer's own shell).
        // There is deliberately no fallback: a release signed with a public key could be
        // replaced by anyone, and the updater trusts "signed by the key this install has".
        create("release") {
            val keystore = System.getenv("MEMORA_KEYSTORE_FILE")?.takeIf { it.isNotBlank() && file(it).exists() }
            if (keystore != null) {
                storeFile = file(keystore)
                storePassword = System.getenv("MEMORA_KEYSTORE_PASSWORD")
                keyAlias = System.getenv("MEMORA_KEY_ALIAS")
                keyPassword = System.getenv("MEMORA_KEY_PASSWORD")
            }
        }
    }

    buildTypes {
        debug {
            // Its own package and name, so a debug build installs beside the release app
            // instead of replacing it (and keeps its own data).
            applicationIdSuffix = ".debug"
            versionNameSuffix = "-debug"
            manifestPlaceholders["appLabel"] = "Memora Debug"
            signingConfig = signingConfigs.getByName("debug")
        }
        release {
            // R8: shrinks and optimises the code and drops unused resources.
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
            signingConfig = signingConfigs.getByName(if (allowDebugSigning) "debug" else "release")
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    buildFeatures {
        compose = true
    }

    testOptions {
        unitTests.isIncludeAndroidResources = true
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
    // Runs tests that need Android classes (SQLite, Base64, AlarmManager) on the JVM.
    testImplementation("org.robolectric:robolectric:4.14.1")
    testImplementation("androidx.test:core:1.6.1")
    constraints {
        // Robolectric (tests only, never in the app) brings older versions with published advisories.
        testImplementation("org.bouncycastle:bcprov-jdk18on:1.85.2") {
            because("GHSA-574f-3g2m-x479, GHSA-9pwp-9qqc-pr26, GHSA-c3fc-8qff-9hwx, GHSA-qp49-qgx5-5m26: fixed in 1.85")
        }
        testImplementation("com.google.guava:guava:33.7.2-jre") {
            because("GHSA-xxph-c9ww-hj94: fixed in 33.7.2")
        }
    }
}

// A release must be signed with the real key unless -PallowDebugSigning=true was passed.
tasks.configureEach {
    if (!allowDebugSigning && (name == "validateSigningRelease" || name == "packageRelease")) {
        doFirst {
            val real = System.getenv("MEMORA_KEYSTORE_FILE")?.takeIf { it.isNotBlank() && file(it).exists() } != null
            if (!real) throw GradleException("Release builds need MEMORA_KEYSTORE_FILE (and the other MEMORA_* signing variables). See docs/DEVELOPMENT.md.")
        }
    }
}

// Every resolved dependency version is pinned in gradle.lockfile, so a build can't pick up a
// different library version than the one reviewed. After changing a dependency, run:
//   gradlew assembleDebug assembleRelease testDebugUnitTest lintDebug cyclonedxBom --write-locks -PallowDebugSigning=true
dependencyLocking {
    lockAllConfigurations()
}

// The SBOM lists only what ships inside the APK.
tasks.named<org.cyclonedx.gradle.CycloneDxTask>("cyclonedxBom") {
    setIncludeConfigs(listOf("releaseRuntimeClasspath"))
    setOutputName("bom")
    setOutputFormat("json")
}

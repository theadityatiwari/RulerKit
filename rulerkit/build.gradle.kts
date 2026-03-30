plugins {
    alias(libs.plugins.android.library)
    alias(libs.plugins.kotlin.android)
    `maven-publish`
}

android {
    namespace = "com.nativeknights.rulerkit"
    compileSdk = 36

    defaultConfig {
        minSdk = 24

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        consumerProguardFiles("consumer-rules.pro")
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }
    kotlinOptions {
        jvmTarget = "11"
    }
}

/*
 * Future KMP migration path:
 *
 * When adding Compose Multiplatform support (Phase 4), this module will be converted to:
 *   plugins { kotlin("multiplatform"); id("com.android.library") }
 *
 * Source sets will split into:
 *   src/commonMain/kotlin/com/nativeknights/rulerkit/  ← pure Kotlin logic (state, units, math)
 *   src/androidMain/kotlin/com/nativeknights/rulerkit/ ← View system + Android Compose
 *   src/iosMain/kotlin/com/nativeknights/rulerkit/     ← iOS (CMP)
 *   src/desktopMain/kotlin/com/nativeknights/rulerkit/ ← Desktop (CMP)
 *
 * Keep logic that has zero Android imports in separate files — those become commonMain as-is.
 */

afterEvaluate {
    publishing {
        publications {
            create<MavenPublication>("release") {
                from(components["release"])
                groupId    = "com.github.nativeknights"
                artifactId = "rulerkit"
                version    = "1.0.0"
            }
        }
    }
}

dependencies {
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.appcompat)
    implementation(libs.material)
    testImplementation(libs.junit)
    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.espresso.core)
}

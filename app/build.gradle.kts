plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
}

android {
    namespace = "com.example.nestedlist"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.example.nestedlist"
        minSdk = 24          // Android 7.0 — covers ~99% of active devices
        targetSdk = 35
        versionCode = 1
        versionName = "1.0"
    }

    buildTypes {
        release {
            // No shrinking for a demo, so the APK builds with zero extra setup.
            isMinifyEnabled = false
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
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
        compose = true
    }
}

dependencies {
    implementation("androidx.core:core-ktx:1.13.1")
    implementation("androidx.activity:activity-compose:1.9.3")

    // The Compose BOM keeps all Compose artifact versions aligned.
    val composeBom = platform("androidx.compose:compose-bom:2024.12.01")
    implementation(composeBom)

    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-tooling-preview")
    implementation("androidx.compose.material3:material3")
    // Provides Icons.Filled.CheckBox / CheckBoxOutlineBlank used by the demo.
    implementation("androidx.compose.material:material-icons-extended")

    // Tooling for the @Preview, debug builds only.
    debugImplementation("androidx.compose.ui:ui-tooling")
}

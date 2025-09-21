plugins {

    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)

    id("com.google.gms.google-services") // ✅ Required for Firebase
}

android {
    namespace = "com.example.secure_share"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.example.secure_share"
        minSdk = 24
        targetSdk = 35
        versionCode = 1
        versionName = "1.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
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
    buildFeatures {
        compose = true
    }
}

dependencies {

    // ✅ Ensure Firebase BOM is first
    implementation(platform(libs.firebase.bom))

    // ✅ Core AndroidX Libraries
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.appcompat)
    implementation(libs.material)
    implementation(libs.androidx.activity)
    implementation(libs.androidx.constraintlayout)

    // ✅ Jetpack Compose UI
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.ui)
    implementation(libs.androidx.ui.graphics)
    implementation(libs.androidx.ui.tooling.preview)
    implementation(libs.androidx.material3)

    // ✅ Firebase Dependencies
    implementation(libs.firebase.auth) // 🔥 Firebase Auth
    implementation(libs.firebase.database.ktx) // 🔥 Firebase Realtime Database
    implementation(libs.firebase.analytics)
    implementation(libs.firebase.firestore.ktx)
    implementation(libs.firebase.storage.ktx)
    implementation(libs.firebase.crashlytics.buildtools)
    implementation(libs.firebase.functions.ktx) // 🔥 Firebase Analytics

    // ✅ Testing
    testImplementation(libs.junit)
    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.espresso.core)
    androidTestImplementation(platform(libs.androidx.compose.bom))
    androidTestImplementation(libs.androidx.ui.test.junit4)
    debugImplementation(libs.androidx.ui.tooling)
    debugImplementation(libs.androidx.ui.test.manifest)

    implementation(platform("com.google.firebase:firebase-bom:32.2.3"))

    // ✅ Firebase Dependencies (Correct syntax)
    implementation("com.google.firebase:firebase-storage-ktx") // Use this if using Kotlin
    // implementation("com.google.firebase:firebase-storage") // Uncomment if using Java



}

import java.util.Properties

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
    kotlin("kapt") // Needed for code generation
    id("com.google.dagger.hilt.android")
    id("org.jetbrains.kotlin.plugin.serialization")
}

fun getLocalProperty(key: String): String {
    val properties = Properties()
    val localPropertiesFile = rootProject.file("local.properties")
    if (localPropertiesFile.exists()) {
        localPropertiesFile.inputStream().use { properties.load(it) }
    }
    return properties.getProperty(key) ?: ""
}

android {
    namespace = "com.example.vishnu"
    compileSdk {
        version = release(36)
    }

    defaultConfig {
        applicationId = "com.example.vishnu"
        minSdk = 26
        targetSdk = 35
        versionCode = 1
        versionName = "1.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"

        val mapsKey = getLocalProperty("MAPS_API_KEY")
        buildConfigField("String", "MAPS_API_KEY", "\"$mapsKey\"")
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
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions {
        jvmTarget = "17"
    }
    buildFeatures {
        compose = true
        buildConfig = true
    }
}

dependencies {
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.activity.compose)
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.graphics)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.material3)
    implementation(libs.androidx.navigation.compose)
    testImplementation(libs.junit)
    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.espresso.core)
    androidTestImplementation(platform(libs.androidx.compose.bom))
    androidTestImplementation(libs.androidx.compose.ui.test.junit4)
    debugImplementation(libs.androidx.compose.ui.tooling)
    debugImplementation(libs.androidx.compose.ui.test.manifest)

    implementation("io.coil-kt:coil-compose:2.5.0")

    // Material Icons Extended (For the filled icons like 'PlayCircle')
    implementation("androidx.compose.material:material-icons-extended:1.6.0")

    // Media3 (ExoPlayer) - The industry standard for video
    implementation("androidx.media3:media3-exoplayer:1.2.0")
    implementation("androidx.media3:media3-ui:1.2.0")
    implementation("androidx.media3:media3-common:1.2.0")

    implementation("com.google.dagger:hilt-android:2.50")
    kapt("com.google.dagger:hilt-android-compiler:2.50")

    // Hilt Integration with Navigation Compose (Crucial for hiltViewModel())
    implementation("androidx.hilt:hilt-navigation-compose:1.1.0")

    // ViewModel & Lifecycle (You likely have these, but check)
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.7.0")

    implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.6.3")

    // Supabase Client
    implementation(platform("io.github.jan-tennert.supabase:bom:3.0.0"))
    implementation("io.github.jan-tennert.supabase:postgrest-kt")
    implementation("io.github.jan-tennert.supabase:auth-kt")

    // Ktor (Network Engine for Supabase)
    implementation("io.ktor:ktor-client-android:3.0.1")

    implementation("androidx.datastore:datastore-preferences:1.0.0")

    // Location Services
    implementation("com.google.android.gms:play-services-location:21.0.1")

    // Permission Handling in Compose (Makes life much easier)
    implementation("com.google.accompanist:accompanist-permissions:0.34.0")

    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-play-services:1.7.3")

    implementation("androidx.activity:activity-compose:1.8.0")

    implementation("com.razorpay:checkout:1.6.33")

    implementation("io.github.jan-tennert.supabase:storage-kt:3.0.0") // Check for latest version
    
    // Google Maps for Android
    implementation("com.google.android.gms:play-services-maps:18.2.0")
    implementation("com.google.maps.android:maps-compose:4.3.0")
    implementation("com.google.maps.android:maps-compose-utils:4.3.0")
    
    // Supabase Realtime for live location updates
    implementation("io.github.jan-tennert.supabase:realtime-kt:3.0.0")

    implementation("com.squareup.retrofit2:retrofit:2.9.0")
    // GSON Converter (easiest for parsing Google Maps JSON)
    implementation("com.squareup.retrofit2:converter-gson:2.9.0")

    implementation("com.exyte:animated-navigation-bar:1.0.0")
}
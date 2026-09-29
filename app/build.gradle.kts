plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
    id("org.jetbrains.kotlin.plugin.serialization")
}

android {
    namespace = "com.daydreamin.app"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.daydreamin.app"
        minSdk = 26
        targetSdk = 35
        versionCode = 5
        versionName = "1.2.1"
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
        }
        debug {
            isMinifyEnabled = false
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

    packaging {
        resources {
            excludes += "/META-INF/{AL2.0,LGPL2.1}"
        }
    }
}

dependencies {
    val composeBom = platform("androidx.compose:compose-bom:2024.09.02")
    implementation(composeBom)
    androidTestImplementation(composeBom)

    implementation("androidx.core:core-ktx:1.13.1")
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.8.6")
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.8.6")
    implementation("androidx.lifecycle:lifecycle-runtime-compose:2.8.6")
    implementation("androidx.activity:activity-compose:1.9.2")

    // Compose UI
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-graphics")
    implementation("androidx.compose.ui:ui-tooling-preview")
    implementation("androidx.compose.material:material-icons-extended")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.foundation:foundation")
    debugImplementation("androidx.compose.ui:ui-tooling")

    // Navigation
    implementation("androidx.navigation:navigation-compose:2.8.1")

    // Media3 — playback, MediaSession, lock screen / notification controls
    implementation("androidx.media3:media3-exoplayer:1.4.1")
    implementation("androidx.media3:media3-session:1.4.1")
    implementation("androidx.media3:media3-common:1.4.1")
    // StandaloneDatabaseProvider — backs the on-disk audio cache's index (media3-datasource,
    // which SimpleCache/CacheDataSource live in, already comes in transitively via exoplayer).
    implementation("androidx.media3:media3-database:1.4.1")

    // Networking
    implementation("com.squareup.retrofit2:retrofit:2.11.0")
    implementation("com.squareup.retrofit2:converter-kotlinx-serialization:2.11.0")
    implementation("com.squareup.okhttp3:okhttp:4.12.0")
    implementation("com.squareup.okhttp3:logging-interceptor:4.12.0")
    implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.7.1")

    // Image loading
    implementation("io.coil-kt:coil-compose:2.7.0")
    // Pulls a representative color out of album artwork for the dynamic glows (Home, mini player).
    implementation("androidx.palette:palette-ktx:1.0.0")

    // Preferences / settings persistence
    implementation("androidx.datastore:datastore-preferences:1.1.1")

    // Splash screen API
    implementation("androidx.core:core-splashscreen:1.0.1")

    // On-device YouTube search + stream extraction — no backend needed to resolve playable audio.
    // Pin to a recent release: YouTube's internal formats shift and older extractor versions stop working.
    implementation("com.github.TeamNewPipe:NewPipeExtractor:v0.26.5")
    implementation("org.jsoup:jsoup:1.18.1")

    // Real backdrop blur / frosted-glass effect (Apple Music-style "liquid glass")
    implementation("dev.chrisbanes.haze:haze:1.5.3")
    implementation("dev.chrisbanes.haze:haze-materials:1.5.3")

    testImplementation("junit:junit:4.13.2")
    androidTestImplementation("androidx.test.ext:junit:1.2.1")
    androidTestImplementation("androidx.test.espresso:espresso-core:3.6.1")
}

plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.multiplatform")
    id("org.jetbrains.compose")
    id("org.jetbrains.kotlin.plugin.compose")
    id("org.jetbrains.kotlin.plugin.serialization")
}

android {
    namespace = "com.daydreamin.app"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.daydreamin.app"
        // Android 5.0: older and 32-bit phones run 5.x–7.x, and an APK whose minimum is above the
        // phone's version doesn't install at all ("There was a problem parsing the package").
        minSdk = 21
        targetSdk = 35
        versionCode = 20
        versionName = "1.6.4"
        // Where installed apps look for new releases. Overridable only to test the updater locally.
        val updateUrl = (project.findProperty("updateUrl") as String?)
            ?: "https://raw.githubusercontent.com/theankitverse/daydreamin-android/main/version.json"
        buildConfigField("String", "UPDATE_URL", "\"$updateUrl\"")
        // Test builds pass -PusageTestMode=true so their usage pings land in TelemetryDeck's test view, not the real counts.
        buildConfigField("boolean", "USAGE_TEST_MODE", (project.findProperty("usageTestMode") as String? ?: "false"))
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
            // Same key as every build already out there (they were all debug builds), so this
            // installs as an update over them instead of needing an uninstall first.
            signingConfig = signingConfigs.getByName("debug")
        }
        debug {
            isMinifyEnabled = false
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
        // java.time and newer java.util.concurrent calls (ours and NewPipe's) on Android 5–7.
        isCoreLibraryDesugaringEnabled = true
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

kotlin {
    // PlayerController, Downloads and a few others are declared once in commonMain and implemented per platform.
    compilerOptions { freeCompilerArgs.add("-Xexpect-actual-classes") }
    androidTarget {
        compilerOptions { jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17) }
    }
    // Mac, Windows and Linux: the same app on the desktop (see /desktop plan). Empty until code moves into commonMain.
    jvm("desktop")

    sourceSets {
        // Everything that isn't tied to one platform: screens, theme, YouTube, library, recommendations.
        commonMain.dependencies {
            implementation(compose.runtime)
            implementation(compose.foundation)
            implementation(compose.ui)
            implementation(compose.material3)
            implementation(compose.materialIconsExtended)
            implementation(compose.components.resources)
            implementation("org.jetbrains.androidx.lifecycle:lifecycle-viewmodel-compose:2.8.4")
            implementation("org.jetbrains.androidx.lifecycle:lifecycle-runtime-compose:2.8.4")
            implementation("org.jetbrains.androidx.navigation:navigation-compose:2.8.0-alpha10")

            // Networking
            implementation("com.squareup.retrofit2:retrofit:2.11.0")
            implementation("com.squareup.retrofit2:converter-kotlinx-serialization:2.11.0")
            implementation("com.squareup.okhttp3:okhttp:4.12.0")
            implementation("com.squareup.okhttp3:logging-interceptor:4.12.0")
            implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.7.1")

            // Image loading
            implementation("io.coil-kt.coil3:coil-compose:3.0.4")
            implementation("io.coil-kt.coil3:coil-network-okhttp:3.0.4")

            // Preferences / settings persistence (the file itself is opened per platform)
            implementation("androidx.datastore:datastore-preferences-core:1.1.1")

            // On-device YouTube search + stream extraction — no backend needed to resolve playable audio.
            // Pin to a recent release: YouTube's internal formats shift and older extractor versions stop working.
            implementation("com.github.TeamNewPipe:NewPipeExtractor:v0.26.5")
            implementation("org.jsoup:jsoup:1.18.1")

            // Real backdrop blur / frosted-glass effect (Apple Music-style "liquid glass")
            implementation("dev.chrisbanes.haze:haze:1.5.3")
            implementation("dev.chrisbanes.haze:haze-materials:1.5.3")
        }
        androidMain.dependencies {
            implementation("androidx.core:core-ktx:1.13.1")
            implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.8.6")
            implementation("androidx.activity:activity-compose:1.9.2")
            implementation("androidx.compose.ui:ui-tooling-preview")

            // Media3 — playback, MediaSession, lock screen / notification controls
            implementation("androidx.media3:media3-exoplayer:1.8.0")
            implementation("androidx.media3:media3-session:1.8.0")
            implementation("androidx.media3:media3-common:1.8.0")
            // StandaloneDatabaseProvider — backs the on-disk audio cache's index (media3-datasource,
            // which SimpleCache/CacheDataSource live in, already comes in transitively via exoplayer).
            implementation("androidx.media3:media3-database:1.8.0")

            // Pulls a representative color out of album artwork for the dynamic glows (Home, mini player).
            implementation("androidx.palette:palette-ktx:1.0.0")
            // The app's existing settings file (Context.preferencesDataStore).
            implementation("androidx.datastore:datastore-preferences:1.1.1")

            // Splash screen API
            implementation("androidx.core:core-splashscreen:1.0.1")
        }
        val desktopMain by getting {
            // The desktop interface uses Compose's desktop click/hover APIs (double-click, right-click).
            languageSettings.optIn("androidx.compose.foundation.ExperimentalFoundationApi")
            languageSettings.optIn("androidx.compose.ui.ExperimentalComposeUiApi")
            dependencies {
                implementation(compose.desktop.currentOs)
                implementation("org.jetbrains.kotlinx:kotlinx-coroutines-swing:1.9.0")
                // Decodes YouTube's Opus audio in plain Java — no native libraries to ship per platform.
                implementation("io.github.jaredmdobson:concentus:1.0.2")
                // The system's media controls: macOS's Now Playing and Windows' media keys through
                // JNA; Linux's MPRIS over D-Bus.
                // The window's own title bar (window buttons inside the app's top bar), on the
                // JetBrains Runtime the app ships with; elsewhere the system's frame stays.
                implementation("org.jetbrains.runtime:jbr-api:1.5.0")
                implementation("net.java.dev.jna:jna:5.19.1")
                implementation("net.java.dev.jna:jna-platform:5.19.1")
                implementation("com.github.hypfvieh:dbus-java-core:5.2.2")
                // junixsocket: handles both socket files and Linux's abstract sockets (unix:abstract=…),
                // which some desktops' session buses use; Java's own Unix sockets can't reach those.
                implementation("com.github.hypfvieh:dbus-java-transport-junixsocket:5.2.2")
            }
        }
        val desktopTest by getting {
            dependencies {
                implementation(kotlin("test"))
            }
        }
        val androidUnitTest by getting {
            dependencies {
                implementation("junit:junit:4.13.2")
                // Real Android classes (Uri and friends) for unit tests that need them.
                implementation("org.robolectric:robolectric:4.14.1")
            }
        }
    }
}

dependencies {
    coreLibraryDesugaring("com.android.tools:desugar_jdk_libs_nio:2.1.2")
    val composeBom = platform("androidx.compose:compose-bom:2024.09.02")
    implementation(composeBom)
    androidTestImplementation(composeBom)
    debugImplementation("androidx.compose.ui:ui-tooling")
    androidTestImplementation("androidx.test.ext:junit:1.2.1")
    androidTestImplementation("androidx.test.espresso:espresso-core:3.6.1")
}

compose.resources {
    packageOfResClass = "com.daydreamin.app.resources"
}

// The desktop app's version, numbered on its own (apart from Android's). Releases take it from their
// tag (desktop-v1.2.3 → -PdesktopVersion=1.2.3); the update check compares the number made from it.
val desktopVersion = (project.findProperty("desktopVersion") as String?) ?: "1.0.0"
val desktopVersionCode = desktopVersion.split('.').map { it.toInt() }.let { (major, minor, patch) -> major * 10_000 + minor * 100 + patch }

compose.desktop {
    application {
        mainClass = "com.daydreamin.app.MainKt"
        // jpackage needs a full JDK; Android Studio's bundled one has none. -PpackagingJdk=… picks one locally; CI uses JAVA_HOME.
        (project.findProperty("packagingJdk") as String?)?.let { javaHome = it }
        jvmArgs += listOf("-Ddaydreamin.version=$desktopVersion", "-Ddaydreamin.versionCode=$desktopVersionCode", "-Xmx512m")

        nativeDistributions {
            targetFormats(
                org.jetbrains.compose.desktop.application.dsl.TargetFormat.Dmg,
                org.jetbrains.compose.desktop.application.dsl.TargetFormat.Msi,
                org.jetbrains.compose.desktop.application.dsl.TargetFormat.Deb,
                org.jetbrains.compose.desktop.application.dsl.TargetFormat.Rpm,
            )
            packageName = "Daydreamin"
            packageVersion = desktopVersion
            description = "Music from everywhere, played beautifully"
            vendor = "Ankit"
            copyright = "© 2026 Ankit"
            // Java modules the app uses: sound and windows, networking with TLS, D-Bus on Linux, and
            // jdk.dynalink for the JavaScript engine NewPipe runs to unlock YouTube's streams.
            modules(
                "java.desktop", "java.naming", "java.net.http", "java.sql", "java.management", "java.instrument",
                "jdk.crypto.ec", "jdk.dynalink", "jdk.security.auth", "jdk.unsupported", "jdk.net", "jdk.accessibility",
            )

            macOS {
                bundleID = "com.daydreamin.desktop"
                iconFile.set(project.file("desktop-icons/daydreamin.icns"))
                appCategory = "public.app-category.music"
                dockName = "Daydreamin"
            }
            windows {
                iconFile.set(project.file("desktop-icons/daydreamin.ico"))
                menuGroup = "Daydreamin"
                shortcut = true
                dirChooser = true
                perUserInstall = true
                // Fixed for good: it's how Windows knows a newer installer is an update of this app.
                upgradeUuid = "6F3B2A1C-8D4E-4F5A-9B7C-2E1D0A9F8C34"
            }
            linux {
                iconFile.set(project.file("desktop-icons/daydreamin.png"))
                packageName = "daydreamin"
                appCategory = "Audio"
                menuGroup = "AudioVideo;Audio;Player"
                shortcut = true
            }
        }
    }
}

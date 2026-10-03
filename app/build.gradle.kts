import java.util.Properties

plugins {
    alias(libs.plugins.androidx.baselineprofile)
    // AGP 9 compiles Kotlin itself, so the Kotlin Android plugin is gone. The
    // two Kotlin COMPILER plugins are still applied by hand: Compose (required
    // since Kotlin 2.0) and serialization.
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.kotlin.serialization)
}

// Release signing comes from keystore.properties when it exists (a local build),
// or from the environment when CI builds a tagged release. Neither is committed.
// Without either, a release build falls back to the debug key so the project
// still assembles for anyone who clones it — it just will not be installable as
// an update over a properly signed copy, which is the honest behaviour.
val keystoreProperties = Properties().apply {
    val file = rootProject.file("keystore.properties")
    if (file.exists()) file.inputStream().use { load(it) }
}
fun signing(key: String, env: String): String? =
    keystoreProperties.getProperty(key) ?: System.getenv(env)

android {
    namespace = "com.cineverse.app"
    compileSdk = 37

    defaultConfig {
        applicationId = "com.cineverse.app"
        // Android 12. Everything the app leans on — dynamic colour, the splash
        // API, rich haptics, Glance widgets — starts here, and the one newer
        // thing (predictive back) is announced to the system and ignored below
        // Android 13 rather than gated in code.
        minSdk = 31
        targetSdk = 37
        // CI passes these in from the tag it is building; a local build uses
        // the defaults, so a clone compiles with no arguments.
        versionCode = (project.findProperty("versionCode") as String?)?.toIntOrNull() ?: 10000
        versionName = (project.findProperty("versionName") as String?) ?: "1.0.0"
        vectorDrawables.useSupportLibrary = true
    }

    signingConfigs {
        create("release") {
            val storeFilePath = signing("storeFile", "CV_KEYSTORE_PATH")
            if (storeFilePath != null && file(storeFilePath).exists()) {
                storeFile = file(storeFilePath)
                storePassword = signing("storePassword", "CV_KEYSTORE_PASSWORD")
                keyAlias = signing("keyAlias", "CV_KEY_ALIAS")
                keyPassword = signing("keyPassword", "CV_KEY_PASSWORD")
            }
        }
    }

    buildTypes {
        debug {
            applicationIdSuffix = ".debug"
            versionNameSuffix = "-debug"
            isMinifyEnabled = false
        }
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
            val release = signingConfigs.getByName("release")
            signingConfig = if (release.storeFile != null) release else signingConfigs.getByName("debug")
        }

        // The two build types the baseline profile plugin adds, given their own
        // application ids.
        //
        // They are installed and uninstalled by the generator, and by default
        // they carry the SAME id as the real app -- so generating a profile on a
        // phone that has CineVerse on it replaces that install and wipes its
        // data, which signs the owner out of their own library. Found the hard
        // way. A suffix costs nothing: an ART profile names classes, and class
        // names do not change with the application id.
        create("nonMinifiedRelease") {
            initWith(getByName("release"))
            applicationIdSuffix = ".benchmark"
            isMinifyEnabled = false
            isShrinkResources = false
            matchingFallbacks += listOf("release")
            // Profiling needs the stack frames that a profileable build keeps.
            isProfileable = true
        }
        create("benchmarkRelease") {
            initWith(getByName("release"))
            applicationIdSuffix = ".benchmark"
            matchingFallbacks += listOf("release")
            isProfileable = true
        }
    }

    testOptions {
        unitTests {
            // Robolectric needs the real resources and the real manifest.
            isIncludeAndroidResources = true
            all {
                it.systemProperty(
                    "robolectric.graphicsMode",
                    "NATIVE",
                )
                // `-Proborazzi.test.record=true` rewrites the references;
                // without it the task compares against what is checked in.
                it.systemProperty(
                    "roborazzi.test.record",
                    project.findProperty("roborazzi.test.record")?.toString() ?: "false",
                )
                it.systemProperty(
                    "roborazzi.test.verify",
                    project.findProperty("roborazzi.test.verify")?.toString() ?: "false",
                )
            }
        }
    }

    buildFeatures {
        compose = true
        buildConfig = true
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
        isCoreLibraryDesugaringEnabled = true
    }

    packaging {
        resources.excludes += setOf(
            "/META-INF/{AL2.0,LGPL2.1}",
            "/META-INF/DEPENDENCIES",
            "META-INF/versions/9/OSGI-INF/MANIFEST.MF",
        )
    }

    androidResources {
        // The locale list is written by hand in res/xml/locales_config.xml, so
        // the generator would only fight it. Per-app language still works.
        generateLocaleConfig = false
    }
}

kotlin {
    compilerOptions {
        jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17)
        freeCompilerArgs.addAll("-opt-in=kotlin.RequiresOptIn")
    }
}

dependencies {
    // The other half of a baseline profile. Without this the generated
    // `baseline-prof.txt` ships inside the APK and is never installed, which is
    // a silent no-op rather than an error -- the kind of thing that looks done
    // and is not.
    implementation(libs.androidx.profileinstaller)

    // Screenshot tests. Robolectric renders Compose on the JVM, Roborazzi saves
    // the pixels -- so these run in CI with no device attached, which is the
    // only way a screenshot test gets run often enough to be worth having.
    testImplementation(libs.junit)
    testImplementation(libs.robolectric)
    testImplementation(libs.roborazzi)
    testImplementation(libs.roborazzi.compose)
    testImplementation(platform(libs.compose.bom))
    testImplementation(libs.compose.ui.test.junit4)
    debugImplementation(libs.compose.ui.test.manifest)

    // The producer. Without this the generate task has no inputs and reports
    // UP-TO-DATE forever, which looks exactly like success.
    baselineProfile(project(":baselineprofile"))

    coreLibraryDesugaring(libs.desugar.jdk.libs)

    implementation(platform(libs.compose.bom))
    implementation(libs.compose.ui)
    implementation(libs.compose.graphics)
    implementation(libs.compose.tooling.preview)
    implementation(libs.compose.material3)
    implementation(libs.compose.material.icons)
    implementation(libs.compose.animation)
    implementation(libs.compose.foundation)
    debugImplementation(libs.compose.tooling)

    implementation(libs.androidx.core)
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.lifecycle.runtime)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.navigation.compose)
    implementation(libs.androidx.datastore)
    implementation(libs.androidx.work)
    implementation(libs.androidx.splashscreen)
    implementation(libs.androidx.paging.compose)
    implementation(libs.androidx.browser)
    implementation(libs.androidx.webkit)
    implementation(libs.mlkit.genai.summarization)
    implementation(libs.androidx.glance.appwidget)
    implementation(libs.androidx.glance.material3)

    implementation(libs.kotlinx.coroutines)
    implementation(libs.kotlinx.coroutines.play.services)
    implementation(libs.kotlinx.serialization.json)

    implementation(libs.retrofit)
    implementation(libs.retrofit.serialization)
    implementation(libs.okhttp)
    implementation(libs.okhttp.logging)

    implementation(libs.coil.compose)
    implementation(libs.coil.network)
    implementation(libs.media3.exoplayer)
    implementation(libs.media3.ui.compose)

    implementation(platform(libs.firebase.bom))
    implementation(libs.firebase.auth)
    implementation(libs.firebase.firestore)

    implementation(libs.credentials)
    implementation(libs.credentials.play.services)
    implementation(libs.googleid)
}

baselineProfile {
    /**
     * Keep the profile under version control.
     *
     * The alternative is regenerating it on every CI run, which needs a device
     * in CI and makes the output of a build depend on how busy that device was.
     * A checked-in profile is reviewable, reproducible, and regenerated
     * deliberately with `./gradlew :app:generateBaselineProfile` when the start-up
     * path actually changes.
     */
    saveInSrc = true
    automaticGenerationDuringBuild = false
}

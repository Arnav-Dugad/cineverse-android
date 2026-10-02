plugins {
    alias(libs.plugins.android.test)
    alias(libs.plugins.androidx.baselineprofile)
}

/**
 * The module that writes the profile.
 *
 * It is a test module, not a library: it drives the real app on a real device
 * through the journeys people actually take, records which classes and methods
 * were used, and hands the list to the app so ART can compile them ahead of
 * time. The pay-off is on FIRST run after an install, which for a sideloaded
 * app is the run that decides whether a friend keeps it.
 */
android {
    namespace = "com.cineverse.baseline"
    compileSdk = 37

    defaultConfig {
        minSdk = 31
        targetSdk = 37
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    // The generator needs a release-shaped build to profile: profiling a
    // debuggable build measures the debugger, not the app.
    targetProjectPath = ":app"

    // Java and Kotlin have to agree. AGP defaults the Java tasks in a test
    // module to 11 while the Kotlin block below asks for 17, and the build fails
    // with "inconsistent JVM targets" rather than quietly picking one.
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
}

kotlin {
    compilerOptions {
        jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17)
    }
}

baselineProfile {
    // One run is noisy; three and a median is a profile you can trust.
    // Anything higher costs minutes of device time for a result that does not
    // move.
    useConnectedDevices = true
}

dependencies {
    implementation(libs.androidx.test.ext.junit)
    implementation(libs.androidx.test.uiautomator)
    implementation(libs.androidx.benchmark.macro)
    implementation(libs.junit)
}

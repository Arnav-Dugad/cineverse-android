plugins {
    alias(libs.plugins.android.application) apply false
    // Declared here so both modules resolve the SAME version. Declaring
    // `com.android.test` only in the subproject fails: AGP is already on the
    // classpath from the app module, and Gradle refuses to check compatibility
    // against a version it was not told about at the root.
    alias(libs.plugins.android.test) apply false
    alias(libs.plugins.androidx.baselineprofile) apply false
    alias(libs.plugins.kotlin.compose) apply false
    alias(libs.plugins.kotlin.serialization) apply false
}

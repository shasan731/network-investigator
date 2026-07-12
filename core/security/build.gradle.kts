plugins { id("network.android.library") }
android { namespace = "com.shasan731.networkinvestigator.core.security" }
dependencies {
    implementation(project(":core:model"))
    implementation(libs.biometric)
    testImplementation(libs.junit)
}

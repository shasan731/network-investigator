plugins { id("network.android.library") }
android { namespace = "com.shasan731.networkinvestigator.core.common" }
dependencies {
    implementation(libs.coroutines.core)
    testImplementation(libs.junit)
}

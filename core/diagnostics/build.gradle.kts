plugins { id("network.android.library") }
android { namespace = "com.shasan731.networkinvestigator.core.diagnostics" }
dependencies {
    api(project(":core:model"))
    implementation(project(":core:common"))
    implementation(project(":core:network"))
    implementation(libs.coroutines.android)
    testImplementation(libs.junit)
    testImplementation(libs.coroutines.core)
}

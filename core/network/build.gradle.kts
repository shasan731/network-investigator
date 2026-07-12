plugins { id("network.android.library"); alias(libs.plugins.kotlin.serialization) }
android { namespace = "com.shasan731.networkinvestigator.core.network" }
dependencies {
    implementation(project(":core:model"))
    implementation(project(":core:security"))
    implementation(libs.okhttp)
    implementation(libs.serialization.json)
    implementation(libs.coroutines.android)
    testImplementation(libs.junit)
    testImplementation(libs.mockwebserver)
}

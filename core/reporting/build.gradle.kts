plugins { id("network.android.library"); alias(libs.plugins.kotlin.serialization) }
android { namespace = "com.shasan731.networkinvestigator.core.reporting" }
dependencies {
    implementation(project(":core:model"))
    implementation(project(":core:security"))
    implementation(libs.serialization.json)
    testImplementation(libs.junit)
}

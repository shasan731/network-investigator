plugins { id("network.android.library"); alias(libs.plugins.kotlin.serialization) }
android { namespace = "com.shasan731.networkinvestigator.core.model" }
dependencies {
    implementation(libs.serialization.json)
    testImplementation(libs.junit)
}

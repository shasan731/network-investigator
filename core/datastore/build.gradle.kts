plugins { id("network.android.library") }
android { namespace = "com.shasan731.networkinvestigator.core.datastore" }
dependencies {
    implementation(libs.datastore.preferences)
    implementation(libs.coroutines.android)
}

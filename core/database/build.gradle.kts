plugins { id("network.android.library"); alias(libs.plugins.ksp); alias(libs.plugins.kotlin.serialization) }
android { namespace = "com.shasan731.networkinvestigator.core.database" }
dependencies {
    implementation(project(":core:model"))
    api(libs.room.runtime)
    implementation(libs.room.ktx)
    implementation(libs.serialization.json)
    ksp(libs.room.compiler)
    testImplementation(libs.junit)
}
ksp { arg("room.schemaLocation", "$projectDir/schemas") }

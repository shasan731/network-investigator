import com.android.build.api.dsl.LibraryExtension
import org.gradle.api.Plugin
import org.gradle.api.Project

class AndroidFeatureConventionPlugin : Plugin<Project> {
    override fun apply(target: Project) = with(target) {
        pluginManager.apply("network.android.library")
        pluginManager.apply("org.jetbrains.kotlin.plugin.compose")
        extensions.configure(LibraryExtension::class.java) {
            buildFeatures.compose = true
        }
        dependencies.add("implementation", dependencies.platform(libs("compose-bom")))
        dependencies.add("implementation", libs("compose-ui"))
        dependencies.add("implementation", libs("compose-material3"))
        dependencies.add("implementation", project(":core:model"))
        dependencies.add("implementation", project(":core:ui"))
    }

    private fun Project.libs(alias: String) =
        extensions.getByType(org.gradle.api.artifacts.VersionCatalogsExtension::class.java)
            .named("libs").findLibrary(alias).get()
}

import com.android.build.api.dsl.CommonExtension
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.artifacts.VersionCatalogsExtension
import org.gradle.kotlin.dsl.configure
import org.gradle.kotlin.dsl.withType
import org.jetbrains.kotlin.compose.compiler.gradle.ComposeCompilerGradlePluginExtension
import org.jetbrains.kotlin.gradle.tasks.KotlinCompile

class ComposePlugin : Plugin<Project> {

  override fun apply(target: Project) {
    target.pluginManager.withPlugin("com.android.application") { configureCompose(target) }
    target.pluginManager.withPlugin("com.android.library") {
      configureCompose(target)
      if (target.path != ":core:snapshot") target.configurePreviewSnapshots()
    }
  }

  private fun configureCompose(target: Project) {
    val libs = target.extensions.getByType(VersionCatalogsExtension::class.java).named("libs")
    target.plugins.apply("org.jetbrains.kotlin.plugin.compose")
    target.dependencies.add("implementation", target.dependencies.platform(libs.findLibrary("compose-bom").get()))
    target.dependencies.add("implementation", libs.findBundle("compose").get())
    target.dependencies.add("debugImplementation", libs.findLibrary("compose-ui-tooling").get())
    target.dependencies.add("testImplementation", libs.findLibrary("molecule").get())
    target.dependencies.add("testImplementation", libs.findLibrary("compose-ui-test-junit4").get())
    target.dependencies.add("debugImplementation", libs.findLibrary("compose-ui-test-manifest").get())
    target.extensions.configure<ComposeCompilerGradlePluginExtension> {
      if (target.providers.gradleProperty("app.composeCompilerReports").orNull?.toBooleanStrictOrNull() == true) {
        reportsDestination.set(target.layout.buildDirectory.dir("compose_compiler"))
      }
      stabilityConfigurationFiles.add(target.layout.settingsDirectory.file("compose_stability.conf"))
    }
    target.extensions.configure<CommonExtension> {
      buildFeatures.compose = true
    }
    target.tasks.withType<KotlinCompile>().configureEach {
      compilerOptions {
        optIn.addAll(
          "androidx.compose.material3.ExperimentalMaterial3Api",
          "androidx.compose.foundation.ExperimentalFoundationApi",
          "androidx.compose.ui.ExperimentalComposeUiApi",
          "androidx.compose.animation.ExperimentalAnimationApi",
          "androidx.compose.foundation.layout.ExperimentalLayoutApi",
        )
      }
    }
  }
}

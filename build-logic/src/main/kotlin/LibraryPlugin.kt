import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.tasks.testing.Test

class LibraryPlugin : Plugin<Project> {

  override fun apply(target: Project) {
    target.pluginManager.run {
      apply("convention.ktlint")
      apply("com.android.library")
      withPlugin("com.android.library") {
        target.baseSetup()
        checkModuleDependencies(target)
        target.tasks.withType(Test::class.java).configureEach {
          // every module takes part in appUnitTest, also the ones without tests yet
          failOnNoDiscoveredTests.set(false)
        }
        target.tasks.register("appUnitTest") {
          dependsOn("testDebugUnitTest")
        }
      }
    }
  }
}

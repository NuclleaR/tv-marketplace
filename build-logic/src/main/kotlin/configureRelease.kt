import com.android.build.api.dsl.ApplicationExtension
import org.gradle.api.Project
import org.gradle.kotlin.dsl.configure
import java.io.File

// CI passes the version (-Papp.versionName, -Papp.versionCode) and the release key (android.injected.signing.*).
// Locally a release signs with the machine-wide key from ~/.gradle/gradle.properties (release.storeFile,
// release.storePassword, release.keyAlias, release.keyPassword), or with the debug key when there is none.
fun Project.configureRelease() {
  extensions.configure<ApplicationExtension> {
    defaultConfig.apply {
      versionName = providers.gradleProperty("app.versionName").orNull ?: "1.0.0"
      versionCode = providers.gradleProperty("app.versionCode").orNull?.toInt() ?: 1
    }
    val storeFile = providers.gradleProperty("release.storeFile").orNull?.let(::File)?.takeIf(File::isFile)
    val releaseSigning = if (storeFile != null) {
      signingConfigs.create("release") {
        this.storeFile = storeFile
        storePassword = providers.gradleProperty("release.storePassword").get()
        keyAlias = providers.gradleProperty("release.keyAlias").get()
        keyPassword = providers.gradleProperty("release.keyPassword").get()
      }
    } else {
      signingConfigs.getByName("debug")
    }
    buildTypes.getByName("release").signingConfig = releaseSigning
  }
}

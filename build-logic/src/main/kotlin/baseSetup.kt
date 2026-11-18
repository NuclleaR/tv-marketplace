import com.android.build.api.dsl.ApplicationDefaultConfig
import com.android.build.api.dsl.CommonExtension
import org.gradle.api.JavaVersion
import org.gradle.api.Project
import org.gradle.api.artifacts.VersionCatalog
import org.gradle.api.artifacts.VersionCatalogsExtension
import org.gradle.api.tasks.testing.Test
import org.gradle.jvm.toolchain.JavaLanguageVersion
import org.gradle.kotlin.dsl.configure
import org.gradle.kotlin.dsl.withType
import org.jetbrains.kotlin.gradle.dsl.JvmTarget
import org.jetbrains.kotlin.gradle.dsl.KotlinProjectExtension
import org.jetbrains.kotlin.gradle.tasks.KotlinCompile
import org.jetbrains.kotlin.powerassert.gradle.PowerAssertCompilationFilter
import org.jetbrains.kotlin.powerassert.gradle.PowerAssertGradleExtension

fun Project.baseSetup() {
  val libs: VersionCatalog = extensions.getByType(VersionCatalogsExtension::class.java).named("libs")
  configurePowerAssert(libs)

  val rootPackage = providers.gradleProperty("app.rootPackage").get()
  val jvmBytecodeVersion = libs.findVersion("jvm-bytecode").get().requiredVersion.toInt()
  val jvmToolchainVersion = libs.findVersion("jvm-toolchain").get().requiredVersion.toInt()
  tasks.withType<KotlinCompile>().configureEach {
    compilerOptions {
      jvmTarget.set(JvmTarget.fromTarget(jvmBytecodeVersion.toString()))
      freeCompilerArgs.add("-Xreturn-value-checker=full")
      optIn.addAll(
        "kotlin.RequiresOptIn",
        "kotlinx.coroutines.ExperimentalCoroutinesApi",
        "kotlinx.coroutines.FlowPreview",
      )
      allWarningsAsErrors.set(providers.gradleProperty("app.warningsAsErrors").map(String::toBooleanStrict))
    }
  }
  tasks.withType(Test::class.java).configureEach {
    maxParallelForks = (Runtime.getRuntime().availableProcessors() / 2).coerceAtLeast(1)
  }
  extensions.configure<KotlinProjectExtension> {
    jvmToolchain {
      languageVersion.set(JavaLanguageVersion.of(jvmToolchainVersion))
    }
  }
  configureRobolectricSdk(this)
  extensions.configure<CommonExtension> {
    // :features:home becomes <rootPackage>.features.home, and :app the root package itself
    namespace = if (path == ":app") rootPackage else rootPackage + "." + path.removePrefix(":").replace(':', '.')
    compileOptions.apply {
      sourceCompatibility = JavaVersion.toVersion(jvmBytecodeVersion)
      targetCompatibility = JavaVersion.toVersion(jvmBytecodeVersion)
    }
    defaultConfig.apply {
      if (this is ApplicationDefaultConfig) {
        targetSdk = libs.findVersion("sdk-target").get().requiredVersion.toInt()
      }
      minSdk = libs.findVersion("sdk-min").get().requiredVersion.toInt()
    }
    compileSdk = libs.findVersion("sdk-compile").get().requiredVersion.toInt()
    testOptions.apply {
      unitTests.isReturnDefaultValues = true
      unitTests.isIncludeAndroidResources = true
    }
    lint.lintConfig = layout.settingsDirectory.file("lint.xml").asFile
  }
  dependencies.run {
    add("implementation", libs.findLibrary("coroutines-core").get())
    add("implementation", libs.findLibrary("coroutines-android").get())
    add("testImplementation", libs.findBundle("testing-jvm").get())
  }
}

@Suppress("OPT_IN_USAGE")
private fun Project.configurePowerAssert(libs: VersionCatalog) {
  pluginManager.apply("org.jetbrains.kotlin.plugin.power-assert")
  extensions.configure<PowerAssertGradleExtension> {
    addRuntimeDependency.set(false)
    functions.addAll(
      "kotlin.assert",
      "kotlin.test.assertTrue",
      "kotlin.test.assertEquals",
      "kotlin.test.assertNull",
    )
    compilationFilter.set(
      PowerAssertCompilationFilter {
        it.name.endsWith("UnitTest")
      },
    )
  }
  dependencies.add("testImplementation", libs.findLibrary("kotlin-powerAssert-runtime").get())
}

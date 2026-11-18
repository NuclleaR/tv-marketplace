plugins {
  `kotlin-dsl`
}

dependencies {
  implementation(libs.androidPluginForGradle)
  implementation(libs.kotlin.pluginForGradle)
  implementation(libs.kotlin.powerAssert)
  implementation(libs.compose.compiler.gradlePlugin)
  implementation(libs.ktlint.gradlePlugin)
}

gradlePlugin {
  plugins {
    create("app") {
      id = "convention.app"
      implementationClass = "AppPlugin"
    }
    create("library") {
      id = "convention.library"
      implementationClass = "LibraryPlugin"
    }
    create("compose") {
      id = "convention.compose"
      implementationClass = "ComposePlugin"
    }
    create("ktlint") {
      id = "convention.ktlint"
      implementationClass = "KtlintPlugin"
    }
  }
}

kotlin {
  jvmToolchain {
    languageVersion.set(JavaLanguageVersion.of(libs.versions.jvm.toolchain.get().toInt()))
  }
}

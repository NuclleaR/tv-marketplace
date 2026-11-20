@file:Suppress("UnstableApiUsage")

rootProject.name = "tv-marketplace"

enableFeaturePreview("TYPESAFE_PROJECT_ACCESSORS")

pluginManagement {
  includeBuild("build-logic")
  repositories {
    google()
    mavenCentral()
    gradlePluginPortal()
  }
}

plugins {
  id("org.gradle.toolchains.foojay-resolver-convention") version "1.0.0"
}

dependencyResolutionManagement {
  repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
  repositories {
    google()
    mavenCentral()
    // only our own libraries built from GitHub tags (tv-keyboard), nothing else resolves from JitPack
    exclusiveContent {
      forRepository { maven("https://jitpack.io") }
      filter { includeGroup("com.github.NuclleaR") }
    }
  }
}

include(":app")
include(":baselineprofile")
include(":navigation")
include(":companion")
include(":core:common")
include(":core:domain")
include(":core:protocol")
include(":core:linkserver")
include(":core:strings")
include(":core:snapshot")
include(":core:testing")
include(":core:ui")
include(":core:data:api")
include(":core:data:impl")
include(":features:home")
include(":features:addapp")
include(":features:inbox")
include(":features:settings")

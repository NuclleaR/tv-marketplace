plugins {
  id("convention.library")
  alias(libs.plugins.metro)
  alias(libs.plugins.kotlin.serialization)
  alias(libs.plugins.ksp)
  alias(libs.plugins.room)
}

room {
  schemaDirectory("$projectDir/schemas")
}

// MigrationTestHelper reads the exported schemas as test assets
extensions.configure<com.android.build.api.dsl.LibraryExtension> {
  sourceSets.getByName("test").assets.directories.add("$projectDir/schemas")
}

dependencies {
  api(projects.core.data.api)
  implementation(projects.core.common)
  implementation(libs.room.runtime)
  ksp(libs.room.compiler)
  implementation(libs.datastore.preferences)
  implementation(libs.ktor.client.core)
  implementation(libs.ktor.client.okhttp)
  implementation(libs.ktor.client.contentNegotiation)
  implementation(libs.ktor.serialization.json)
  implementation(libs.serialization.json)
  testImplementation(libs.ktor.client.mock)
  testImplementation(libs.room.testing)
}

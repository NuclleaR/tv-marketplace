plugins {
  id("convention.library")
  id("convention.compose")
}

// renders every @Preview of a module to PNG: ./gradlew :<module>:snapshot
dependencies {
  api(platform(libs.compose.bom))
  api(libs.composablePreviewScanner)
  api(libs.compose.ui.test.junit4)
  api(libs.robolectric)
  api(libs.androidx.test.core)
  api(libs.androidx.test.junit)
  api(libs.junit)
}

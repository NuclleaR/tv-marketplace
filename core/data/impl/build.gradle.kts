plugins {
  id("convention.library")
  alias(libs.plugins.metro)
}

dependencies {
  api(projects.core.data.api)
}

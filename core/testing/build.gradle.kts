plugins {
  id("convention.library")
}

dependencies {
  api(projects.core.data.api)
  api(projects.core.common)
}

plugins {
  id("convention.library")
}

dependencies {
  api(projects.core.data.api)
  api(projects.core.common)
  api(projects.core.installer.api)
}

plugins {
  id("convention.library")
  alias(libs.plugins.metro)
}

dependencies {
  // WorkerCreator hands out WorkManager workers, so its types are part of this module's API
  api(libs.work.runtime)
}

pluginManagement {
  val kotlinVersion = "1.9.24"
  repositories {
    gradlePluginPortal()
  }

  plugins {
    // realization
    kotlin("multiplatform") version kotlinVersion
    kotlin("plugin.serialization") version kotlinVersion
  }
  resolutionStrategy {
    eachPlugin {
      if (requested.id.id == "kotlin2js") {
        useModule("org.jetbrains.kotlin:kotlin-gradle-plugin:${requested.version}")
      }
    }
  }
}

rootProject.name = "workflow-dispatch-action"

import com.rnett.action.addWebpackGenTask
import org.jetbrains.kotlin.gradle.plugin.KotlinDependencyHandler
import org.jetbrains.kotlin.gradle.targets.js.dsl.ExperimentalDistributionDsl
import org.jetbrains.kotlin.gradle.targets.js.webpack.KotlinWebpackConfig

plugins {
  kotlin("multiplatform") version "2.3.10"
  kotlin("plugin.serialization") version "2.3.10"
  id("com.github.rnett.ktjs-github-action") version "1.6.0"
}

repositories {
  mavenCentral()
}

kotlin {
  js(IR) {
    val outputDir = layout.projectDirectory.dir("dist")
    val outFileName = "index.js"
    val webpackTask = addWebpackGenTask()
    binaries.executable()
    browser {
      @OptIn(ExperimentalDistributionDsl::class)
      distribution {
        outputDirectory = outputDir
        distributionName = outFileName
      }
      webpackTask {
        if (mode == KotlinWebpackConfig.Mode.PRODUCTION) {
          output.globalObject = "this"
          sourceMaps = false
          mainOutputFileName = outFileName

          dependsOn(webpackTask)
        }
      }
    }
    nodejs {
      version = "20.9.0"
    }

    tasks.clean.configure {
      delete(outputDir)
    }
  }

  sourceSets {
    val jsMain by getting {
      dependencies {
        listOf("kotlin-js-action", "serialization").forEach {
          implementation("com.github.rnett.ktjs-github-action:$it:1.6.0")
        }
        implementation("app.softwork:kotlinx-uuid-core-js:0.1.7")
        implementation(project.dependencies.platform("io.ktor:ktor-bom:3.4.0"))
        implementation("io.ktor:ktor-client-js")
      }
    }
  }
}

fun KotlinDependencyHandler.implementation(
  group: String,
  name: String,
  version: String? = null,
  configure: ExternalModuleDependency.() -> Unit = {}
): ExternalModuleDependency {
  val depNot = listOfNotNull(group, name, version).joinToString(":")
  return implementation(depNot, configure)
}

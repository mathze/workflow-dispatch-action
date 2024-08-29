import com.rnett.action.addWebpackGenTask
import org.jetbrains.kotlin.gradle.plugin.KotlinDependencyHandler
import org.jetbrains.kotlin.gradle.targets.js.dsl.ExperimentalDistributionDsl
import org.jetbrains.kotlin.gradle.targets.js.nodejs.NodeJsRootExtension
import org.jetbrains.kotlin.gradle.targets.js.nodejs.NodeJsRootPlugin
import org.jetbrains.kotlin.gradle.targets.js.webpack.KotlinWebpackConfig

plugins {
  kotlin("multiplatform")
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

    tasks.clean.configure {
      delete(outputDir)
    }

    rootProject.plugins.withType<NodeJsRootPlugin> {
      rootProject.the<NodeJsRootExtension>().version = "20.9.0"
    }
  }

  sourceSets {
    val jsMain by getting {
      dependencies {
        listOf("kotlin-js-action", "serialization").forEach {
          implementation(group = "com.github.rnett.ktjs-github-action", name = it, version = "1.6.0")
        }
        implementation(group = "app.softwork", name = "kotlinx-uuid-core-js", version = "0.1.2")
        implementation(group = "io.ktor", name = "ktor-client-js", version = "2.3.12")
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

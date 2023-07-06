import com.rnett.action.githubAction
import org.jetbrains.kotlin.gradle.plugin.KotlinDependencyHandler

plugins {
  kotlin("multiplatform")
  id("com.github.rnett.ktjs-github-action") version "1.6.0"
}

repositories {
  mavenCentral()
}

kotlin {
  js(IR) {
    githubAction()
  }
  sourceSets {
    val jsMain by getting {
      dependencies {
        listOf("kotlin-js-action", "serialization").forEach {
          implementation(group = "com.github.rnett.ktjs-github-action", name = it, version = "1.6.0")
        }
        implementation(group = "app.softwork", name = "kotlinx-uuid-core-js", version = "0.0.20")
        implementation(group = "io.ktor", name = "ktor-client-js", version = "2.3.1")
      }
    }
  }
}

fun KotlinDependencyHandler.implementation(group: String, name: String, version:String? = null, configure: org.gradle.api.artifacts.ExternalModuleDependency.() -> kotlin.Unit = {}): org.gradle.api.artifacts.ExternalModuleDependency {
  val depNot = listOfNotNull(group, name, version).joinToString(":")
  return implementation(depNot, configure)
}

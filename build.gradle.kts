import com.rnett.action.githubAction
import org.jetbrains.kotlin.gradle.targets.js.nodejs.NodeJsRootExtension
import org.jetbrains.kotlin.gradle.targets.js.nodejs.NodeJsRootPlugin

plugins {
  kotlin("js")
  id("com.github.rnett.ktjs-github-action") version "1.6.0"
}

repositories {
  mavenCentral()
}

kotlin {
  js(IR) {
    githubAction()
  }
}

plugins.withType<NodeJsRootPlugin> {
  configure<NodeJsRootExtension> {
    nodeVersion = "16.18.0"
  }
}

dependencies {
  listOf("kotlin-js-action", "serialization").forEach {
    implementation(group = "com.github.rnett.ktjs-github-action", name = it, version = "1.6.0")
  }

  implementation(group = "app.softwork", name = "kotlinx-uuid-core-js", version = "0.0.17")
  implementation(group = "io.ktor", name = "ktor-client-js", version = "2.2.4")
}

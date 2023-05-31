import com.rnett.action.githubAction

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

dependencies {
  listOf("kotlin-js-action", "serialization").forEach {
    implementation(group = "com.github.rnett.ktjs-github-action", name = it, version = "1.6.0")
  }

  implementation(group = "app.softwork", name = "kotlinx-uuid-core-js", version = "0.0.20")
  implementation(group = "io.ktor", name = "ktor-client-js", version = "2.3.1")
}

import com.rnett.action.githubAction

plugins {
  kotlin("js")
  id("com.github.rnett.ktjs-github-action") version "1.5.0"
}

repositories {
  mavenCentral()
  // required by task `packageJson` -> config `npm` -> dependency `kotlinx-nodejs`
  // (see https://github.com/Kotlin/kotlinx-nodejs/issues/16)
  jcenter()
}

kotlin {
  js(IR) {
    githubAction()
  }
}

dependencies {
  listOf("kotlin-js-action", "serialization").forEach {
    implementation(group = "com.github.rnett.ktjs-github-action", name = it, version = "1.4.3")
  }

  implementation(group = "app.softwork", name = "kotlinx-uuid-core-js", version = "0.0.12")
  implementation(group = "io.ktor", name = "ktor-client-js", version = "1.6.6")
}
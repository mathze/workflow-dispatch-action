import com.rnett.action.generateAutoBuildWorkflow
import com.rnett.action.githubAction

plugins {
  kotlin("js")
//  kotlin("plugin.serialization")
  id("com.github.rnett.ktjs-github-action") version "1.4.3"
}

repositories {
  mavenCentral()
  // required by kotlinx-nodejs (see https://github.com/Kotlin/kotlinx-nodejs/issues/16)
  jcenter()
}

kotlin {
  js(IR) {
    githubAction()
  }
}

generateAutoBuildWorkflow()

dependencies {
  listOf("kotlin-js-action", "serialization").forEach {
    implementation(group = "com.github.rnett.ktjs-github-action", name = it, version = "1.4.3")
  }

  implementation(group="app.softwork", name="kotlinx-uuid-core-js", version="0.0.9")
  implementation(kotlin("stdlib-js"))
  implementation("io.ktor:ktor-client-js:1.6.4")
}
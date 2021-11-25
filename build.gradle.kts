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
  implementation(group="com.github.rnett.ktjs-github-action", name="kotlin-js-action", version="1.4.3")
  implementation(kotlin("stdlib-js"))

  implementation("io.ktor:ktor-client-js:1.6.4")
  implementation("io.ktor:ktor-client-serialization:1.6.4")
  implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.3.1")
}
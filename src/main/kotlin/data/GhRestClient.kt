package data

import io.ktor.client.request.HttpRequestBuilder
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.request.post
import io.ktor.http.HttpHeaders
import io.ktor.http.takeFrom
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import utils.actions.ActionEnvironment

class GhRestClient(token: String, private val owner: String, private val repo: String) : WsClient(token) {

  suspend fun sendPost(path: String, body: String): JsonElement {
    return Json.parseToJsonElement(client.post {
      ghDefaults(createRepoPath(path))
      this.body = body
    })
  }

  suspend fun sendGet(path: String, additionalConfig: (HttpRequestBuilder.() -> Unit)? = null): JsonElement {
    return Json.parseToJsonElement(client.get {
      ghDefaults(createRepoPath(path))
      if (null != additionalConfig) {
        this.apply(additionalConfig)
      }
    })
  }

  private fun createRepoPath(path: String) = "/repos/$owner/$repo/$path"

  private val restApiUrl by lazy {
    ActionEnvironment.GITHUB_API_URL
  }

  private fun HttpRequestBuilder.ghDefaults(path: String) {
    applyGhDefaults(this)
    header(HttpHeaders.Accept, "application/vnd.github.v3+json")
    url {
      takeFrom("$restApiUrl/$path")
    }
  }
}

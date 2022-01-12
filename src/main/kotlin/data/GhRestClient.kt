package data

import io.ktor.client.request.HttpRequestBuilder
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.request.post
import io.ktor.client.statement.HttpResponse
import io.ktor.client.statement.readText
import io.ktor.http.HttpHeaders
import io.ktor.http.charset
import io.ktor.http.takeFrom
import io.ktor.utils.io.charsets.Charsets
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import utils.actions.ActionEnvironment

class GhRestClient(token: String, private val owner: String, private val repo: String) : WsClient(token) {

  suspend fun sendPost(path: String, body: String): HttpResponse {
    return client.post {
      ghDefaults(createRepoPath(path))
      this.body = body
    }
  }

  suspend fun sendGet(path: String, additionalConfig: (HttpRequestBuilder.() -> Unit)? = null): HttpResponse {
    return client.get {
      ghDefaults(createRepoPath(path))
      if (null != additionalConfig) {
        this.apply(additionalConfig)
      }
    }
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

fun HttpRequestBuilder.queryParams(params: Map<String, String>) {
  params.forEach { (k, v) ->
    url.parameters[k] = v
  }
}

suspend inline fun HttpResponse.toJson() = Json.parseToJsonElement(
  readText(charset() ?: Charsets.UTF_8)
)
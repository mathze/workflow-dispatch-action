package data

import com.rnett.action.httpclient.HttpResponse
import com.rnett.action.httpclient.MutableHeaders
import io.ktor.http.HttpHeaders
import io.ktor.http.URLBuilder
import io.ktor.http.takeFrom
import utils.actions.ActionEnvironment

class GhRestClient(token: String, private val owner: String, private val repo: String) : WsClient(token) {

  suspend fun sendPost(path: String, body: String): HttpResponse {
    return client.post(createRepoPath(path), body)
  }

  suspend fun sendGet(path: String, query: Map<String, String> = mapOf(), headerProvider: (MutableHeaders.() -> Unit)? = null): HttpResponse {
    return client.get(createRepoPath(path, query)) {
      if (null != headerProvider) {
        headerProvider()
      }
    }
  }

  private fun createRepoPath(path: String, query: Map<String, String> = mapOf()) = URLBuilder()
    .takeFrom("$restApiUrl/repos/$owner/$repo/$path")
    .queryParams(query)
    .buildString()

  private val restApiUrl by lazy {
    ActionEnvironment.GITHUB_API_URL
  }

  override fun applyGhDefaults(headers: MutableHeaders) {
    super.applyGhDefaults(headers)
    headers.add(HttpHeaders.Accept, "application/vnd.github.v3+json")
  }
}

fun URLBuilder.queryParams(params: Map<String, String>): URLBuilder = this.also {
  params.forEach { (k, v) ->
    parameters[k] = v
  }
}

fun HttpResponse.etag() = headers["etag"]
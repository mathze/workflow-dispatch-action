package data

import com.rnett.action.httpclient.MutableHeaders
import io.ktor.client.request.HttpRequestBuilder
import io.ktor.http.HttpHeaders
import io.ktor.http.takeFrom
import kotlinx.serialization.json.JsonElement
import utils.actions.ActionEnvironment

class GhGraphClient(token: String) : WsClient(token) {

  suspend fun sendQuery(query: String): JsonElement {
    return client.post(graphApiUrl, query).toJson()
  }

  private val graphApiUrl by lazy {
    ActionEnvironment.GITHUB_GRAPHQL_URL
  }
  override fun applyGhDefaults(headers: MutableHeaders) {
    super.applyGhDefaults(headers)
    headers.add(HttpHeaders.Accept, "application/json")
  }
  private fun HttpRequestBuilder.ghDefaults() {
    url {
      takeFrom(graphApiUrl)
    }
  }
}
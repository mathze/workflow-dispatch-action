package data

import io.ktor.client.request.HttpRequestBuilder
import io.ktor.client.request.header
import io.ktor.client.request.post
import io.ktor.http.HttpHeaders
import io.ktor.http.takeFrom
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import utils.actions.ActionEnvironment

class GhGraphClient(token: String) : WsClient(token) {

  suspend fun sendQuery(query: String): JsonElement {
    return Json.parseToJsonElement(
      client.post {
        ghDefaults()
        body = query
      }
    )
  }

  private val graphApiUrl by lazy {
    ActionEnvironment.GITHUB_GRAPHQL_URL
  }

  private fun HttpRequestBuilder.ghDefaults() {
    applyGhDefaults(this)
    header(HttpHeaders.Accept, "application/json")
    url {
      takeFrom(graphApiUrl)
    }
  }
}
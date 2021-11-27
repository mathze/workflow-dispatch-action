package data

import com.rnett.action.core.logger.debug
import com.rnett.action.core.logger.info
import com.rnett.action.httpclient.MutableHeaders
import io.ktor.client.request.HttpRequestBuilder
import io.ktor.http.HttpHeaders
import io.ktor.http.takeFrom
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import utils.actions.ActionEnvironment

class GhGraphClient(token: String) : WsClient(token) {

  suspend fun sendQuery(query: String, variables: JsonObject? = null): JsonElement {
    debug("Sending request >>$query<< to $graphApiUrl")
    val req = buildJsonObject {
      put("query", JsonPrimitive(query))
      variables?.let {
        this.put("variables", variables)
      }
    }

    val response = client.post(graphApiUrl, req.toString())
    val result = response.toJson()
    info("Response $result")
    return result
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
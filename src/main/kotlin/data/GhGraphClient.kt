package data

import com.rnett.action.core.logger.debug
import com.rnett.action.httpclient.MutableHeaders
import io.ktor.http.HttpHeaders
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import utils.actions.ActionEnvironment

/**
 * Convenient class to handle requests to GitHub's GraphQL endpoint.
 *
 * @property[token] The PAT to use for authentication.
 * @constructor Create an instance.
 */
class GhGraphClient(token: String) : WsClient(token) {

  /**
   * Sends the given query to GitHub's GraphQL endpoint.
   *
   * @param[query] The query to send.
   * @param[variables] Optional additional variables to use in the query.
   *
   * @return The received response as json.
   */
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
    debug("Response $result")
    return result
  }

  /**
   * The url of the GraphQL endpoint.
   */
  private val graphApiUrl by lazy {
    ActionEnvironment.GITHUB_GRAPHQL_URL
  }

  /**
   * Extend headers by `Accept` header.
   */
  override fun applyHeaders(headers: MutableHeaders) {
    super.applyHeaders(headers)
    headers.add(HttpHeaders.Accept, "application/json")
  }
}
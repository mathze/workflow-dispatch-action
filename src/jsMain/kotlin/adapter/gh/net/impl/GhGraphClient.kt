package adapter.gh.net.impl

import adapter.gh.net.GraphQlClient
import adapter.gh.net.impl.GhRestClient.HttpHeaders
import com.rnett.action.core.logger.debug
import com.rnett.action.httpclient.HttpResponse
import com.rnett.action.httpclient.MutableHeaders
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
class GhGraphClient(token: String) : WsClient(token), GraphQlClient {

  override suspend fun sendQuery(query: String, variables: JsonObject?): HttpResponse {
    debug("Sending request >>$query<< to $graphApiUrl")
    val req = buildJsonObject {
      put("query", JsonPrimitive(query))
      variables?.let {
        this.put("variables", variables)
      }
    }
    debug("Final request: $req")

    val response = client.post(graphApiUrl, req.toString())
    return response
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

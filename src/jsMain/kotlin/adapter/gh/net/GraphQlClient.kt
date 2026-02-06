package adapter.gh.net

import com.rnett.action.httpclient.HttpResponse
import kotlinx.serialization.json.JsonObject

interface GraphQlClient {

  /**
   * Sends the given query to GitHub's GraphQL endpoint.
   *
   * @param[query] The query to send.
   * @param[variables] Optional additional variables to use in the query.
   *
   * @return The received response as json.
   */
  suspend fun sendQuery(query: String, variables: JsonObject? = null): HttpResponse
}

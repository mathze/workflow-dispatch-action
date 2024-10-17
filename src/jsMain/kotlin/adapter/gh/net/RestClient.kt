package adapter.gh.net

import adapter.gh.net.impl.WsClient
import com.rnett.action.httpclient.HttpResponse
import com.rnett.action.httpclient.MutableHeaders

interface RestClient {

  /**
   * Send a POST request to GitHub's rest-api.
   *
   * @param[pathOrUrl] The path ('actions/workflows') to the resource or a full qualified url.
   *  Attention: If using a URL this should point to a GitHub-resource,
   *  otherwise you may have to override some default headers set by [WsClient].
   * @param[body] The request body to send.
   *
   * @return The response object of the request.
   */
  suspend fun sendPost(pathOrUrl: String, body: String): HttpResponse

  /**
   * Sends a GET request to GitHub's rest-api.
   *
   * @param[pathOrUrl] The path to the resource or a full qualified url.
   *  Attention: If using a URL this should point to a GitHub-resource,
   *  otherwise you may have to override some default headers set by [WsClient].
   * @param[query] Query parameters to add to the path. Defaults to empty map.
   * @param[headerProvider] Function to manipulate the headers of the request.
   *
   * @return The response object of the request.
   */
  suspend fun sendGet(
    pathOrUrl: String,
    query: Map<String, String> = mapOf(),
    headerProvider: (MutableHeaders.() -> Unit)? = null
  ): HttpResponse

}

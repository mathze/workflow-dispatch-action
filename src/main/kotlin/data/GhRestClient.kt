package data

import com.rnett.action.httpclient.HttpResponse
import com.rnett.action.httpclient.MutableHeaders
import io.ktor.http.HttpHeaders
import io.ktor.http.URLBuilder
import io.ktor.http.takeFrom
import utils.actions.ActionEnvironment

/**
 * Convenient class to handle requests to GitHub's rest-api.
 *
 * @constructor Create an instance.
 * 
 * General Remarks: If using any request method with a path, this path is prepended by the owner and repo.
 *
 * @param[token] See [WsClient.token]
 * @param[owner] Used to build the main part of GitHub resource paths.
 * @param[repo] Used to build the main part of GitHub resource paths.
 */
class GhRestClient(token: String, private val owner: String, private val repo: String) : WsClient(token) {

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
  suspend fun sendPost(pathOrUrl: String, body: String): HttpResponse {
    return client.post(createUrl(pathOrUrl), body)
  }

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
  ): HttpResponse {
    return client.get(createUrl(pathOrUrl, query)) {
      if (null != headerProvider) {
        headerProvider()
      }
    }
  }

  /**
   * Create the target url and adds additional query parameter to it.
   *
   * The target URL will be build either based on [ActionEnvironment.GITHUB_API_URL] if just a path is passed.
   * This function distinguishes a path from a URL by whether it starts with 'http'.
   *
   * @param[pathOrUrl] The path to a GitHub resource or a URL.
   * @param[query] The query arguments to append to the URL. Defaults to empty map.
   *
   * @return The configured URLBuilder to use in [HttpClient][com.rnett.action.httpclient.HttpClient]s request methods.
   */
  private fun createUrl(pathOrUrl: String, query: Map<String, String> = mapOf()) = URLBuilder()
    .takeFrom(pathOrUrl.let {
      if (it.startsWith("http")) {
        it
      } else {
        "$restApiUrl/repos/$owner/$repo/$pathOrUrl"
      }
    })
    .queryParams(query)
    .buildString()

  private val restApiUrl by lazy {
    ActionEnvironment.GITHUB_API_URL
  }

  /**
   * Extends default headers with the appropriate 'Accept' header.
   */
  override fun applyHeaders(headers: MutableHeaders) {
    super.applyHeaders(headers)
    headers.add(HttpHeaders.Accept, "application/vnd.github.v3+json")
  }
}

/**
 * Extension function to add query parameters to this URLBuilder.
 */
fun URLBuilder.queryParams(params: Map<String, String>): URLBuilder = this.also {
  params.forEach { (k, v) ->
    parameters[k] = v
  }
}

/**
 * Extension function to retrieve the `etag` header value.
 */
fun HttpResponse.etag() = headers["etag"]
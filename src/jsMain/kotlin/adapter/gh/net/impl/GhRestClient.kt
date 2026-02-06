package adapter.gh.net.impl

import adapter.gh.net.RestClient
import adapter.gh.net.queryParams
import com.rnett.action.core.logger
import com.rnett.action.httpclient.HttpResponse
import com.rnett.action.httpclient.MutableHeaders
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
class GhRestClient(
  token: String,
  private val owner: String,
  private val repo: String) : WsClient(token), RestClient {

  override suspend fun sendPost(pathOrUrl: String, body: String): HttpResponse {
    return client.post(createUrl(pathOrUrl), body)
  }

  override suspend fun sendGet(
    pathOrUrl: String,
    query: Map<String, String>,
    headerProvider: (MutableHeaders.() -> Unit)?
  ): HttpResponse {
    val url = createUrl(pathOrUrl, query)
    logger.debug("sending GET request to $url")
    return client.get(url) {
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
    headers.add(Accept, "application/vnd.github.v3+json")
  }

  @Suppress("ConstPropertyName")
  companion object HttpHeaders {
    const val Accept = "Accept"
    const val CacheControl = "Cache-Control"
    const val UserAgent = "User-Agent"
    const val IfNoneMatch = "If-None-Match"
  }
}

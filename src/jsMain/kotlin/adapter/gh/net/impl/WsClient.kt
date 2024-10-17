package adapter.gh.net.impl

import adapter.gh.net.impl.GhRestClient.HttpHeaders
import com.rnett.action.httpclient.HttpClient
import com.rnett.action.httpclient.MutableHeaders

/**
 * Base implementation to easily interact with GitHub's webservice apis.
 * 
 * @param[token] The PAT used to access GitHub's apis.
 */
abstract class WsClient(private val token: String) {
  protected val client by lazy {
    HttpClient {
      bearerAuth(token)
      headers {
        applyHeaders(this)
      }
    }
  }

  /**
   * Apply some default headers.
   *
   * Additional headers can be added by overriding. Make sure to call super.
   * 
   * @param[headers] The headers instance to extend.
   */
  protected open fun applyHeaders(headers: MutableHeaders) {
    headers.add(HttpHeaders.CacheControl, "no-cache")
    headers.add(HttpHeaders.UserAgent, "mathze/workflow-dispatch-action")
  }
}

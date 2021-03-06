package data

import com.rnett.action.httpclient.HttpClient
import com.rnett.action.httpclient.HttpResponse
import com.rnett.action.httpclient.MutableHeaders
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

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

  companion object {
    /**
     * Header key for use with etags.
     */
    const val HEADER_IF_NONE_MATCH = "If-None-Match"
  }
}

/**
 * Extension to easily get the response body as json object.
 */
suspend inline fun HttpResponse.toJson() = Json.parseToJsonElement(readBody())

/**
 * Extension to convert status code integer representation to [HttpStatusCode].
 */
fun HttpResponse.httpStatus() = HttpStatusCode.fromValue(this.statusCode)

/**
 * Creates a json-object of following structure:
 * ```
 * {
 *  "headers": <JsonObject>,
 *  "body": <string>, // optional
 *  "status-code": <int>,
 *  "status-message": <string>
 *  ""
 * }
 * ```
 *
 * @param withBody Also put the body in the result. This consumes the body, so subsequent calls
 * to readBody will fail!
 * Default: `false`
 * 
 * @return The above described json-structure.
 */
suspend fun HttpResponse.toResponseJson(withBody: Boolean = false): JsonObject {
  val resp = this
  return buildJsonObject {
    put("headers", JsonObject(resp.headers.toMap().mapValues { (_, v) ->
      JsonPrimitive(v)
    }))
    if (withBody) {
      put("body", resp.readBody())
    }
    put("status-code", resp.statusCode)
    put("status-message", resp.statusMessage)
  }
}
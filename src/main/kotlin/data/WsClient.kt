package data

import com.rnett.action.httpclient.HttpClient
import com.rnett.action.httpclient.HttpResponse
import com.rnett.action.httpclient.MutableHeaders
import io.ktor.http.HttpHeaders
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

open class WsClient(private val token: String) {
  protected val client by lazy {
    HttpClient {
      bearerAuth(token)
      headers {
        applyGhDefaults(this)
      }
    }
  }

  protected open fun applyGhDefaults(headers: MutableHeaders) {
    headers.add(HttpHeaders.CacheControl, "no-cache")
    headers.add(HttpHeaders.UserAgent, "mathze/workflow-dispatch-action")
  }
  
  companion object {
    const val HEADER_IF_NONE_MATCH = "If-None-Match"
  }
}

suspend inline fun HttpResponse.toJson() = Json.parseToJsonElement(readBody())

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
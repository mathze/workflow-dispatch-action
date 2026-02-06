package adapter.gh.net

import com.rnett.action.httpclient.HttpResponse
import io.ktor.http.URLBuilder
import io.ktor.http.HttpStatusCode
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put


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

/**
 * Extension function to add query parameters to this URLBuilder.
 */
fun URLBuilder.queryParams(params: Map<String, String>): URLBuilder = this.also {
  params.forEach { (k, v) ->
    parameters[k] = v
  }
}

/**
 * Extension function to retrieve the `eTag` header value.
 */
fun HttpResponse.eTag() = headers["etag"]

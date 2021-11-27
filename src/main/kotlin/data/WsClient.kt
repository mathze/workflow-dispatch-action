package data

import com.rnett.action.httpclient.MutableHeaders
import com.rnett.action.serialization.JsonHttpClient
import com.rnett.action.serialization.JsonHttpResponse
import io.ktor.http.HttpHeaders
import kotlinx.serialization.json.Json

open class WsClient(private val token: String) {
  protected val client by lazy {
    JsonHttpClient {
      bearerAuth(token)
      headers {
        applyGhDefaults(this)
      }
    }
  }

  protected open fun applyGhDefaults(headers: MutableHeaders) {
    headers.add(HttpHeaders.CacheControl, "no-cache")
  }
}

suspend inline fun JsonHttpResponse.toJson() = Json.parseToJsonElement(readBody())
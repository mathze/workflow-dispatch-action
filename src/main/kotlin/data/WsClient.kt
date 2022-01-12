package data

import com.rnett.action.httpclient.HttpClient
import com.rnett.action.httpclient.HttpResponse
import com.rnett.action.httpclient.MutableHeaders
import io.ktor.http.HttpHeaders
import kotlinx.serialization.json.Json

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
}

suspend inline fun HttpResponse.toJson() = Json.parseToJsonElement(readBody())
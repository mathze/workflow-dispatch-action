package data

import io.ktor.client.HttpClient
import io.ktor.client.features.HttpResponseValidator
import io.ktor.client.features.json.JsonFeature
import io.ktor.client.features.json.serializer.KotlinxSerializer
import io.ktor.client.request.HttpRequestBuilder
import io.ktor.client.request.header
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.isSuccess
import kotlinx.serialization.json.Json

open class WsClient(protected val token: String) {
  protected val client by lazy {
    HttpClient {
      install(JsonFeature) {
        serializer = KotlinxSerializer(json = Json {
          ignoreUnknownKeys = true
          coerceInputValues = false
          isLenient = true
        })
      }
      HttpResponseValidator {
        validateResponse {
          when (it.status) {
            HttpStatusCode.BadRequest -> error("Bad request")
            HttpStatusCode.Unauthorized -> error("Unauthorized utils.actions.error")
          }

          if (!it.status.isSuccess()) {
            error("Bad status: ${it.status}")
          }
        }
      }
    }
  }

  protected fun applyGhDefaults(httpRequestBuilder: HttpRequestBuilder) {
    httpRequestBuilder.header(HttpHeaders.Authorization, "token $token")
    httpRequestBuilder.header(HttpHeaders.CacheControl, "no-cache")
  }
}
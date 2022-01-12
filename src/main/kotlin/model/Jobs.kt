package model

import data.GhRestClient
import kotlinx.serialization.json.JsonObject

data class Jobs(
  val url: String,
  var etag: String? = null
) {
  private var jobs = mutableListOf<JsonObject>()

  suspend fun update(client: GhRestClient) {
    
  }
}
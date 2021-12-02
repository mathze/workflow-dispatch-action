package model

import com.rnett.action.core.logger
import data.GhRestClient
import data.WsClient.Companion.HEADER_IF_NONE_MATCH
import data.httpStatus
import data.toJson
import data.toResponseJson
import io.ktor.http.HttpStatusCode
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import utils.actions.ActionFailedException

data class Jobs(
  val url: String
) {
  private var etag: String? = null
  private var jobs = listOf<JsonObject>()

  suspend fun fetchJobs(client: GhRestClient) {
    val response = client.sendGet(url) {
      etag?.let {
        this.add(HEADER_IF_NONE_MATCH, it)
      }
    }

    when {
      HttpStatusCode.NotModified == response.httpStatus() -> {
        logger.debug("Jobs: Not modified")
      }
      response.isSuccess() -> {
        jobs = response.toJson().jsonObject.getValue("jobs").jsonArray.map {
          it.jsonObject
        }.toList()
      }
      else -> {
        throw ActionFailedException("Cannot retrieve jobs from $url! Details:${response.toResponseJson(true)}")
      }
    }
  }

  fun hasJobWithName(name: String): Boolean = jobs.any { job ->
    job["steps"]?.let { steps ->
      steps.jsonArray.any { step ->
        name == step.jsonObject.getValue("name").jsonPrimitive.contentOrNull
      }
    } ?: false
  }
}
package adapter.gh.net.rest

import adapter.gh.net.RestClient
import adapter.gh.net.toJson
import adapter.gh.net.toResponseJson
import com.rnett.action.core.logger
import domain.model.Result
import domain.ports.WorkflowPort
import io.ktor.http.HttpStatusCode
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlin.js.Date

class GhWorkflowAdapter(private val client: RestClient) : WorkflowPort {

  override suspend fun retrieveWorkflowId(workflowName: String): Result<String> {
    val response = client.sendGet("actions/workflows/$workflowName")

    if (!response.isSuccess()) {
      logger.error("Receiving workflow id results in error! Details: ${response.toResponseJson(true)}")
      return Result.error("Unable to receive workflow id for '$workflowName'! Details see log")
    }

    val jsonResponse = response.toJson()
    val content = jsonResponse.jsonObject.getValue("id").jsonPrimitive.contentOrNull
    return if(null == content) {
      Result.error("Unable to get workflow id from response! Got: $jsonResponse")
    } else {
      Result.ok(content)
    }
  }

  override suspend fun triggerWorkflow(
    workflowId: String,
    ref: String,
    inputs: JsonObject?
  ): Result<Date> {
    val body = JsonObject(
      mutableMapOf<String, JsonElement>(
        "ref" to JsonPrimitive(ref)
      ).also {
        if (null != inputs) {
          it["inputs"] = inputs
        }
      }
    ).toString()

    logger.info("Sending workflow dispatch event with body $body")
    val fallBackDate = Date() // keep this here, as this indicates the time when
    val response = client.sendPost("actions/workflows/$workflowId/dispatches", body)
    if (HttpStatusCode.MultipleChoices.value <= response.statusCode) {
      logger.error("Response: ${response.readBody()}")
      return Result.error("Error starting workflow! For response details see log.")
    }

    val rawDate = response.headers["date"]
    val date = if (null != rawDate) {
      Date(rawDate)
    } else {
      logger.warning(
        """No start date received from response, using fallback date $fallBackDate.
          |If you see this message everytime please inform the action developers.""".trimMargin()
      )
      fallBackDate
    }
    logger.info("Dispatched event at '$date'. (Header: ${response.headers.toMap()}\nBody: ${response.readBody()})")
    return Result.ok(date)
  }
}

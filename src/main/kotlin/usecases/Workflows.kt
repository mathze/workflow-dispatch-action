package usecases

import com.rnett.action.core.logger
import data.GhRestClient
import data.toJson
import data.toResponseJson
import io.ktor.http.HttpStatusCode
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import utils.actions.ActionFailedException
import kotlin.js.Date

class Workflows(private val client: GhRestClient) {

  suspend fun getWorkflowIdFromName(wfName: String): String {
    val response = client.sendGet("actions/workflows/$wfName")
    if (!response.isSuccess()) {
      logger.error("Receiving workflow id results in error! Details: ${response.toResponseJson(true)}")
      throw ActionFailedException("Unable to receive workflow id! Details see log")
    }

    val jsonResponse = response.toJson()
    return jsonResponse.jsonObject["id"]?.jsonPrimitive?.contentOrNull
      ?: throw ActionFailedException("Unable to get workflow id from response! Got: $jsonResponse")
  }

  /**
   * Fires the workflow dispatch event for a workflow.
   *
   * @param workflowId The `id` or `name` of the workflow.
   * @param ref The name of the `branch'.
   * @param inputs Additional inputs that will be sent as inputs to the workflow.
   *
   * @return The datetime string (ISO) of creation time (or current date if not received by the endpoint)
   */
  suspend fun triggerWorkflow(workflowId: String, ref: String, inputs: JsonObject? = null): String = logger.withGroup("Triggering workflow") {
    val body = JsonObject(
      mutableMapOf<String, JsonElement>(
        "ref" to JsonPrimitive(ref),
      ).also {
        if (null != inputs) {
          it["inputs"] = inputs
        }
      }
    ).toString()

    logger.info("Sending trigger with body $body")
    val response = client.sendPost("actions/workflows/$workflowId/dispatches", body)
    if (HttpStatusCode.MultipleChoices.value <= response.statusCode) {
      logger.error("Response: ${response.readBody()}")
      throw ActionFailedException("Error starting workflow! Details see log")
    }
    val rawDate =
      response.headers["date"] ?: throw ActionFailedException("No date header found! Got ${response.headers.toMap()}")
    val date = Date(rawDate).toISOString()
    logger.info("Dispatched event at '$date'. (Header: ${response.headers.toMap()}\nBody: ${response.readBody()})")
    return date
  }
}
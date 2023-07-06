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

/**
 * Class to deal with the main use cases trough GitHub's `workflows` resource api.
 *
 * @constructor Create an instance to interact with GitHub's workflow api.
 * @property[client] The client used to send requests to GitHub rest-api.
 */
class Workflows(private val client: GhRestClient) {

  /**
   * Retrieves the technical id of a workflow.
   *
   * Internally the action, as well as GitHub's apis, rely on ids rather than on "human-readable" names.
   *
   * @param wfName Human-readable name of a workflow.
   *
   * @return GitHub's technical id for the workflow name.
   *
   * @throws ActionFailedException If the request does not succeed, or if no 'id' could be retrieved from response.
   */
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
   * @param workflowId The `id` of the workflow.
   * @param ref The name of the `branch` the workflow shall run on.
   * @param inputs Additional data that will be sent as `inputs` to the workflow.
   *
   * @return The datetime string (ISO) of creation time (or current date if not received by the endpoint)
   */
  suspend fun triggerWorkflow(workflowId: String, ref: String, inputs: JsonObject? = null): String =
    logger.withGroup("Triggering workflow") {
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
      val fallBackDate = Date().toISOString()
      val response = client.sendPost("actions/workflows/$workflowId/dispatches", body)
      if (HttpStatusCode.MultipleChoices.value <= response.statusCode) {
        logger.error("Response: ${response.readBody()}")
        throw ActionFailedException("Error starting workflow! For response details see log.")
      }

      val rawDate = response.headers["date"]
      val date = if (null != rawDate) {
        Date(rawDate).toISOString()
      } else {
        logger.warning(
          """No start date received from response, using fallback date $fallBackDate.
          |If you see this message everytime please inform the action developers.""".trimMargin()
        )
        fallBackDate
      }
      logger.info("Dispatched event at '$date'. (Header: ${response.headers.toMap()}\nBody: ${response.readBody()})")
      return date
    }
}

package usecases

import com.rnett.action.core.logger
import data.GhRestClient
import data.etag
import data.toJson
import io.ktor.http.HttpStatusCode
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import model.RunConclusion
import model.RunStatus
import model.WorkflowRun
import setTimeout
import utils.actions.ActionFailedException
import kotlin.js.Date
import kotlin.math.abs
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds
import kotlin.time.DurationUnit
import kotlin.time.toDuration

class Workflows(private val client: GhRestClient) {

  suspend fun findWorkflowId(wfName: String): String {
    val response = client.sendGet("actions/workflows/$wfName")
    return response.toJson().jsonObject["id"]!!.jsonPrimitive.content
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
  suspend fun triggerWorkflow(workflowId: String, ref: String, inputs: JsonObject? = null): String {
    val body = JsonObject(
      mutableMapOf<String, JsonElement>(
        "ref" to JsonPrimitive(ref),
      ).also { 
        if(null != inputs) {
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
    val date = response.headers["date"] ?: throw ActionFailedException("No date header found! Got ${response.headers.toMap()}")
    logger.info("Dispatched event at '$date'. (Header: ${response.headers.toMap()}\nBody: ${response.readBody()})")
    return date
  }

  /**
   * Waits until a workflow run with given criteria exists.
   *
   * @param workflowId The id or name of the workflow the run belongs to.
   * @param createdTime The time the run was created.
   * @param ref The branch or tag the run belongs to.
   * @param maxTimeout The maximum duration to wait for the run to appear. Defaults to 3 seconds
   *
   * @return The found workflow run or `null` if none was found within the timeout.
   */
  suspend fun waitForWorkflowRunCreated(
    workflowId: String,
    createdTime: String,
    ref: String,
    maxTimeout: Duration = 3.seconds
  ): WorkflowRun? = logger.withGroup("Wait workflow run created") {
    val start = Date()
    var result: Pair<String?, WorkflowRun?> = Pair(null, null)
    var delta = 0.seconds
    do {
      result = findWorkflowRun(workflowId, createdTime, ref, result).also {
        if (null == it.second) {
          logger.info("No run found, retry in 1s")
          setTimeout({ }, 1000)
        }
      }
      delta = Date().delta(start)
      logger.info("Time passed since start: $delta")
    } while ((null == result.second) && (delta < maxTimeout))

    return result.second
  }

  private suspend fun findWorkflowRun(
    workflowId: String,
    startTime: String,
    ref: String,
    prev: Pair<String?, WorkflowRun?>
  ): Pair<String?, WorkflowRun?> {
    val query = mapOf(
      queryEvent(),
      queryCreated(startTime),
      queryRef(ref)
    )
    val response = client.sendGet("actions/runs", query) {
      prev.first?.also {
        this.add(HEADER_ETAG, it)
      }
    }

    if (HttpStatusCode.NotModified.value == response.statusCode) {
      // if we got not modified we used an etag -> prev cannot be null
      logger.info("No updates")
      return prev
    }

    val wfRuns = response.toJson().jsonObject.getValue("workflow_runs").jsonArray.map {
      it.jsonObject
    }
    logger.info("Found ${wfRuns.size} matching workflow runs.")
    val runs = wfRuns.filter {
      val wfId = it.getValue("workflow_id").jsonPrimitive.content
      workflowId == wfId
    }.map {
      WorkflowRun(
        it.getValue("id").jsonPrimitive.content,
        null,
        ref,
        RunStatus.from(it.getValue("status").jsonPrimitive.contentOrNull)!!,
        RunConclusion.from(it.getValue("conclusion").jsonPrimitive.contentOrNull)
      )
    }

    return Pair(
      response.etag(),
      runs.firstOrNull()
    )
  }

  private fun Date.delta(other: Date): Duration {
    val msThis = this.getUTCMilliseconds()
    val msOther = other.getUTCMilliseconds()
    val delta = abs(msThis - msOther)
    return delta.toDuration(DurationUnit.MILLISECONDS)
  }

  companion object {
    private const val HEADER_ETAG = "If-None-Match"
    private const val QUERY_EVENT = "event"
    private const val EVENT_DISPATCH = "workflow_dispatch"
    private const val QUERY_CREATED = "created"
    private const val QUERY_REF = "branch"

    fun queryEvent(type: String = EVENT_DISPATCH) = QUERY_EVENT to type
    fun queryCreated(at: String) = QUERY_CREATED to at
    fun queryRef(of: String) = QUERY_REF to of
  }
}
package usecases

import com.rnett.action.core.logger
import com.rnett.action.httpclient.HttpResponse
import data.GhRestClient
import data.WsClient.Companion.HEADER_IF_NONE_MATCH
import data.etag
import data.toJson
import data.toResponseJson
import io.ktor.util.date.getTimeMillis
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import model.Jobs
import model.RunConclusion
import model.RunStatus
import model.WorkflowRun
import utils.actions.ActionFailedException
import utils.delay
import kotlin.math.abs
import kotlin.time.Duration
import kotlin.time.DurationUnit
import kotlin.time.toDuration

class WorkflowRuns(
  private val client: GhRestClient,
) {

  private val runs = mutableListOf<WorkflowRun>()

  /**
   * Waits until a workflow run with given criteria exists.
   * If more than one run was found the first will be returned.
   * If the [externalRunId] is given, the found runs will be checked, no matter if only one was found or more!
   *
   * @param workflowId The id or name of the workflow the run belongs to.
   * @param createdTime The time the run was created.
   * @param ref The branch or tag the run belongs to.
   * @param maxTimeout The maximum duration to wait for the run to appear. Defaults to 3 seconds
   * @param externalRunId The id used to check in step-names
   *
   * @return The found workflow run or `null` if none was found within the timeout.
   */
  suspend fun waitForWorkflowRunCreated(
    workflowId: String,
    dispatchTime: String,
    ref: String,
    maxTimeout: Duration,
    externalRefId: String? = null
  ) {
    val start = getTimeMillis()
    var result: Pair<String?, WorkflowRun?> = Pair(null, null)
    var delta: Duration
  }

  suspend fun waitWorkflowRunCompleted(workflowRunId: String, maxTimeout: Duration, frequency: Duration) {
    
  }

  private fun updateRunDetails(run: WorkflowRun) {

  }

  private suspend fun getRunDetails(runId: String): WorkflowRun {
    val response = sendRunRequest(runId)
    if (!response.isSuccess()) {
      throw ActionFailedException("Received error response on run details! ${response.toResponseJson(true)}")
    }

    val body = response.toJson().jsonObject
    return WorkflowRun(
      runId,
      response.etag(),
      body.getValue("head_branch").jsonPrimitive.content,
      RunStatus.from(body.getValue("status").jsonPrimitive.content)!!,
      RunConclusion.from(body.getValue("conclusion").jsonPrimitive.contentOrNull),
      Jobs(body.getValue("jobs_url").jsonPrimitive.content)
    )
  }

  private suspend fun sendRunRequest(runId: String, etag: String? = null): HttpResponse =
    client.sendGet("/actions/runs/$runId") {
      etag?.also {
        this.add(HEADER_IF_NONE_MATCH, it)
      }
    }
  
  private suspend inline fun <reified T> executePolling(maxTimeout: Duration, frequency: Duration, block: () -> Pair<Boolean, T>): Pair<Boolean, T> {
    val start = getTimeMillis()
    var delta: Duration
    var result: Pair<Boolean, T>
    do {
      result = block().also { 
        if (!it.first) {
          logger.info("No result, retry in $frequency")
          delay(frequency.inWholeMilliseconds)
        }
      }
      delta = getTimeMillis().deltaMs(start)
      logger.debug("Time passed since start: $delta")
    } while (!result.first && delta.inWholeMilliseconds < maxTimeout.inWholeMilliseconds)

    return result
  }

  private fun Long.deltaMs(other: Long): Duration {
    val delta = abs(this - other)
    return delta.toDuration(DurationUnit.MILLISECONDS)
  }
}
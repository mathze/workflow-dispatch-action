package usecases

import com.rnett.action.core.logger
import com.rnett.action.httpclient.HttpResponse
import data.GhRestClient
import data.WsClient.Companion.HEADER_IF_NONE_MATCH
import data.etag
import data.httpStatus
import data.toJson
import data.toResponseJson
import io.ktor.http.HttpStatusCode
import io.ktor.util.date.getTimeMillis
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonArray
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
   * Used in [updateRunList] to reduce food print and save rate limit.
   */
  private var runListEtag: String? = null

  /**
   * Waits until a workflow run with given criteria exists.
   * If more than one run was found the first will be returned.
   * If the [externalRefId] is given, the found runs will be checked, no matter if only one was found or more!
   *
   * @param workflowId The id or name of the workflow the run belongs to.
   * @param dispatchTime The time the run was created.
   * @param ref The branch or tag the run belongs to.
   * @param maxTimeout The maximum duration to wait for the run to appear. Defaults to 3 seconds
   * @param externalRefId The id used to check in step-names
   *
   * @return The found workflow run or `null` if none was found within the timeout.
   */
  suspend fun waitForWorkflowRunCreated(
    workflowId: String,
    dispatchTime: String,
    ref: String,
    maxTimeout: Duration,
    frequency: Duration,
    externalRefId: String? = null
  ): WorkflowRun? {
    val result: Pair<Boolean, WorkflowRun?> = executePolling(maxTimeout, frequency) {
      findWorkflowRun(workflowId, ref, dispatchTime, externalRefId)
    }

    return result.second
  }

  private suspend fun findWorkflowRun(
    workflowId: String, ref: String, dispatchTime: String, externalRefId: String?
  ): Pair<Boolean, WorkflowRun?> {
    // 1. update run list
    updateRunList(workflowId, ref, dispatchTime)
    // 2. update run-details
    runs.forEach { run ->
      updateRunDetails(run)
    }

    // 3. if external ref id is present we check jobs of the runs otherwise we take the first closest to dispatch date
    val candidate = externalRefId?.let { extRefId ->
      // before we check we update the 'jobs' entry
      runs.filter {
        // if we have an external ref id we only can consider runs that have jobs
        it.status != RunStatus.QUEUED
      }.firstOrNull { run ->
        // normally here job should never be null (ensured by updateRunDetails)
        run.jobs!!.let {
          // before we check we update the jobs entry
          it.fetchJobs(client)
          it.hasJobWithName(extRefId)
        }
      }
    } ?: runs.sortedWith(compareBy { it.dateCreated }).firstOrNull()

    return Pair(null != candidate, candidate)
  }

  /**
   * Queries the workflow runs list for runs triggered by dispatch events which were created after a
   * given time for a specified branch.
   * Query will be performed with internal etag.
   *
   *
   * @param workflowId Used to filter the resulting runs
   * @param ref branch to which the run applies
   * @param dispatchTime Gotten from [Workflows.triggerWorkflow], used as query parameter with '>=' operator.
   */
  private suspend fun updateRunList(workflowId: String, ref: String, dispatchTime: String) {
    val queryArgs = mapOf(
      queryEvent(), queryCreated(dispatchTime), queryRef(ref)
    )
    val runsResp = client.sendGet("actions/runs", queryArgs) {
      runListEtag?.also {
        this.add(HEADER_IF_NONE_MATCH, it)
      }
    }

    when (runsResp.httpStatus()) {
      HttpStatusCode.OK -> {
        logger.info("Got workflow runs")
        runListEtag = runsResp.etag()
        // new (no run with id) or updates
        val jsonRuns = runsResp.toJson().jsonObject.getValue("workflow_runs").jsonArray.map {
            it.jsonObject
          }.filter { // we are only interested in those runs that belong to our workflowId
            getWorkflowId(it) == workflowId
          }

        logger.info("${jsonRuns.size} runs left that matches actual workflow.")
        updateRunListFromResponse(jsonRuns)
      }
      HttpStatusCode.NotModified -> {
        logger.info("Run list up-to-date")
      }
      else -> {
        throw ActionFailedException("Unable to retrieve list of workflow runs! Details: ${runsResp.toResponseJson()}")
      }
    }
  }

  private fun updateRunListFromResponse(jsonRuns: List<JsonObject>) {
    val freshRuns = jsonRuns.map { jRun ->
      WorkflowRun(getId(jRun))
    }
    val newRuns = freshRuns.filter { frshRun ->
      runs.none { it.id == frshRun.id }
    }
    logger.info("Found ${newRuns.size} new runs")
    val removedRuns = runs.filter { oldRun -> 
      freshRuns.none { it.id == oldRun.id }
    }
    logger.info("Found ${removedRuns.size} removed runs")
    runs.removeAll(removedRuns)
    runs.addAll(newRuns)
  }

  suspend fun waitWorkflowRunCompleted(workflowRunId: String, maxTimeout: Duration, frequency: Duration): WorkflowRun {
    val runDetails = WorkflowRun(id = workflowRunId)
    val result = executePolling(maxTimeout, frequency) {
      updateRunDetails(runDetails)
      Pair(runDetails.status == RunStatus.COMPLETED, runDetails)
    }
    return result.second
  }

  private suspend fun updateRunDetails(run: WorkflowRun) {
    val runResponse = sendRunRequest(run.id, run.etag)
    if (HttpStatusCode.NotModified == runResponse.httpStatus()) {
      return
    }
    if (!runResponse.isSuccess()) {
      throw ActionFailedException("Received error response on run details! ${runResponse.toResponseJson(true)}")
    }
    // else either new or changed -> update
    val runJson = runResponse.toJson().jsonObject
    run.etag = runResponse.etag()
    run.branch = getHeadBranch(runJson)
    run.status = getRunStatus(runJson)!!
    run.conclusion = getConclusion(runJson)
    run.jobs = getJobs(runJson)
    run.dateCreated = getCreationDate(runJson)
  }

  private suspend fun sendRunRequest(runId: String, etag: String? = null): HttpResponse =
    client.sendGet("actions/runs/$runId") {
      etag?.also {
        this.add(HEADER_IF_NONE_MATCH, it)
      }
    }

  private suspend inline fun <T> executePolling(
    maxTimeout: Duration, frequency: Duration, block: () -> Pair<Boolean, T>
  ): Pair<Boolean, T> {
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
    } while (!result.first && (delta < maxTimeout))

    return result
  }

  private fun Long.deltaMs(other: Long): Duration {
    val delta = abs(this - other)
    return delta.toDuration(DurationUnit.MILLISECONDS)
  }

  companion object {
    private const val QUERY_EVENT = "event"
    private const val EVENT_DISPATCH = "workflow_dispatch"
    private const val QUERY_CREATED = "created"
    private const val QUERY_REF = "branch"

    fun queryEvent(type: String = EVENT_DISPATCH) = QUERY_EVENT to type
    fun queryCreated(at: String) = QUERY_CREATED to ">=$at"
    fun queryRef(of: String) = QUERY_REF to of

    fun getWorkflowId(json: JsonObject) = json.getValue("workflow_id").jsonPrimitive.content
    fun getId(json: JsonObject) = json.getValue("id").jsonPrimitive.content
    fun getHeadBranch(json: JsonObject) = json.getValue("head_branch").jsonPrimitive.content
    fun getRunStatus(json: JsonObject) = RunStatus.from(json.getValue("status").jsonPrimitive.content)
    fun getConclusion(json: JsonObject) = RunConclusion.from(json.getValue("conclusion").jsonPrimitive.contentOrNull)
    fun getCreationDate(json: JsonObject) = json.getValue("created_at").jsonPrimitive.content
    fun getJobs(json: JsonObject) = Jobs(json.getValue("jobs_url").jsonPrimitive.content)
  }
}
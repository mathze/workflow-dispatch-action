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

/**
 *
 * Class to deal with the main use cases trough GitHub's `runs` resource api.
 *
 * @constructor Create an instance to interact with GitHub's runs api.
 * @property[client] The client used to send requests to GitHub's rest-api.
 */
class WorkflowRuns(
  private val client: GhRestClient,
) {

  /**
   * Caches the already known workflow runs.
   */
  private val runs = mutableListOf<WorkflowRun>()

  /**
   * Used in [updateRunList] to reduce food print and spare the rate limit.
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
   * @param maxTimeout The maximum duration to wait for the run to appear.
   * @param frequency The duration to wait between consecutive calls to the api.
   * @param externalRefId The id used to check for in step-names. May be `null` if not marker step is used.
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
  ): WorkflowRun? = logger.withGroup("Trying to detect workflow run id") {
    val result: Pair<Boolean, WorkflowRun?> = executePolling(maxTimeout, frequency) {
      findWorkflowRun(workflowId, ref, dispatchTime, externalRefId)
    }

    return result.second
  }

  /**
   * Finds a workflow run matching the given criteria.
   *
   * @param[workflowId] The id of the workflow the run have to belong to
   * @param[dispatchTime] The time when the workflow dispatch event was triggered.
   *    We only consider runs after this time.
   * @param[externalRefId] Optional, if present we only consider the workflow that
   *  contains a step with the given value as its name.
   *
   * @return Pair where the first member defines if we found a matching workflow and the second member
   *  may contain the found Workflow. Details see [executePolling].
   */
  private suspend fun findWorkflowRun(
    workflowId: String, ref: String, dispatchTime: String, externalRefId: String?
  ): Pair<Boolean, WorkflowRun?> {
    // 1. update run list
    updateRunList(workflowId, ref, dispatchTime)
    // 2. update run-details
    runs.forEach { run ->
      updateRunDetails(run)
    }

    logger.info("Current runs in scope: $runs")

    // 3. if external ref id is present we check jobs of the runs
    val candidate = if (null != externalRefId) {
      runs.filter {
        // if we have an external ref id we only can consider runs that have jobs (in_progress or completed)
        when (it.status) {
          RunStatus.IN_PROGRESS, RunStatus.COMPLETED -> true
          RunStatus.QUEUED, RunStatus.PENDING, RunStatus.REQUESTED, RunStatus.WAITING -> false
        }
      }.firstOrNull { run ->
        // normally here the job should never be null (ensured by updateRunDetails)
        run.jobs?.let {
          // before we check, we update the jobs entry
          it.fetchJobs(client)
          it.hasJobWithName(externalRefId)
        } ?: false // but in case, it is equal to `false` (has no job with name)
      }
    } else { // Otherwise, we take the first closest to dispatch date
      runs.sortedWith(compareBy { it.dateCreated }).firstOrNull()
    }

    return Pair(null != candidate, candidate)
  }

  /**
   * Queries the workflow runs list for runs triggered by dispatch events which were created after a
   * given time for a specified branch.
   * 
   * Query will be performed with internal etag to spare users rate limit.
   *
   * The retrieved runs will be filtered by the actual workflowId this action runs for.
   *
   * Finally, the internal list of runs gets updated by [updateRunListFromResponse].
   *
   * @param workflowId Used to filter the resulting runs.
   * @param ref branch to which the run applies.
   * @param dispatchTime Gotten from [Workflows.triggerWorkflow], used as query parameter with '>=' operator.
   * @throws ActionFailedException In case receiving run details fails.
   */
  private suspend fun updateRunList(workflowId: String, ref: String, dispatchTime: String) {
    val queryArgs = mapOf(
      queryEvent(), queryCreatedAt(dispatchTime), queryRef(ref)
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

  /**
   * Updates the current known run list with the prefiltered fresh runs of [updateRunList].
   *
   * We do not simply replace known runs with the fresh ones, because we already have requested the run details
   * and therefor also have an etag which spare the users rate limit.
   *
   * Flow:
   * - First, transforms the raw json object to an internal representation.
   * - Second, determines runs that were created since last query (in first run all are new).
   * - Third and last, we remove those runs that no longer exists (edge-case if runs where deleted in meantime).
   *
   * @param[jsonRuns] The list of prefiltered 'run' JsonObjects used to update internal run cache.
   *
   * @see [runs]
   */
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

  /**
   * Waits until a workflow run reaches the [complete][RunStatus.COMPLETED] status or the timeout exceeds.
   *
   * Waiting will be performed in a loop using etag to spare users rate limit.
   *
   * @param[workflowRunId] The id of the workflow run for which to wait.
   * @param[maxTimeout] The maximum duration this function shall wait on the status before 'giving up'.
   * @param[frequency] The duration to wait between two consecutive requests.
   *
   * @return Pair where the first member indicates the success and the second member contains the WorkflowRun.
   * @throws ActionFailedException In case updating run details fails.
   * @see [executePolling]
   */
  suspend fun waitWorkflowRunCompleted(
    workflowRunId: String,
    maxTimeout: Duration,
    frequency: Duration
  ): Pair<Boolean, WorkflowRun> {
    val runDetails = WorkflowRun(id = workflowRunId)
    return executePolling(maxTimeout, frequency) {
      updateRunDetails(runDetails)
      Pair(runDetails.status == RunStatus.COMPLETED, runDetails)
    }
  }

  /**
   * Support function to update the workflow run details from json response.
   *
   * Uses etag, if present, to spare users rate limit.
   * The function mutates the passed argument.
   *
   * @param[run] The run to update.
   * @throws ActionFailedException In case receiving run details fails.
   */
  private suspend fun updateRunDetails(run: WorkflowRun) {
    val runResponse = sendRunRequest(run)
    if (HttpStatusCode.NotModified == runResponse.httpStatus()) {
      return
    }
    if (!runResponse.isSuccess()) {
      val rawResp = runResponse.toResponseJson(true)
      throw ActionFailedException("Received error response while retrieving workflow run details! Response: $rawResp")
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

  /**
   * Retrieve the details of a workflow run from the GitHub's rest-api.
   *
   * The request uses the [WorkflowRun.id] and if present the respective [WorkflowRun.etag].
   *
   * @param run The [WorkflowRun] for which to get the details.
   * @return The raw HttpResponse for further processing by the caller.
   */
  private suspend fun sendRunRequest(run: WorkflowRun): HttpResponse =
    client.sendGet("actions/runs/${run.id}") {
      run.etag?.also {
        this.add(HEADER_IF_NONE_MATCH, it)
      }
    }

  /**
   * Support function to execute [block] in a loop.
   *
   * The times block will be invoked depends on the [maxTimeout], the [frequency]
   * and the execution time of [block] itself.
   *
   * To identify if polling can be stopped, [block] has to return a pair which first member
   * is `true`, otherwise function waits [frequency] and executes [block].
   *
   * @param[maxTimeout] The maximum duration of total execute time before returning to caller.
   * @param[frequency] The duration to wait between two consecutive [block] invocations.
   * @param[block] The function to execute in loop.
   *
   * @return The result of [block] invocation.
   */
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
          delay(frequency)
        }
      }
      delta = getTimeMillis().deltaMs(start)
      logger.debug("Time passed since start: $delta")
    } while (!result.first && (delta < maxTimeout))

    return result
  }

  /**
   * Extension to retrieve the duration between two integer timestamps.
   *
   * Example:
   * ```
   * val start = getTimeMillis()
   * // Do some work ...
   * val timePassed = getTimeMillis().deltaMs(start)
   * println("Processing took $timePassed")
   * ```
   * @return The time passed between start and this as [Duration].
   */
  private fun Long.deltaMs(other: Long): Duration {
    val delta = abs(this - other)
    return delta.toDuration(DurationUnit.MILLISECONDS)
  }

  /**
   * Helper object to deal with some common GitHub rest-api stuff.
   */
  companion object {
    private const val QUERY_EVENT = "event"
    private const val EVENT_DISPATCH = "workflow_dispatch"
    private const val QUERY_CREATED_AT = "created"
    private const val QUERY_REF = "branch"

    //<editor-fold desc="Header stuff">
    fun queryEvent(type: String = EVENT_DISPATCH) = QUERY_EVENT to type
    fun queryCreatedAt(at: String) = QUERY_CREATED_AT to ">=$at"
    fun queryRef(of: String) = QUERY_REF to of
    //</editor-fold>

    //<editor-fold desc="Json stuff">
    fun getWorkflowId(json: JsonObject) = json.getValue("workflow_id").jsonPrimitive.content
    fun getId(json: JsonObject) = json.getValue("id").jsonPrimitive.content
    fun getHeadBranch(json: JsonObject) = json.getValue("head_branch").jsonPrimitive.content
    fun getRunStatus(json: JsonObject) = RunStatus.from(json.getValue("status").jsonPrimitive.content)
    fun getConclusion(json: JsonObject) = RunConclusion.from(json.getValue("conclusion").jsonPrimitive.contentOrNull)
    fun getCreationDate(json: JsonObject) = json.getValue("created_at").jsonPrimitive.content
    fun getJobs(json: JsonObject) = Jobs(json.getValue("jobs_url").jsonPrimitive.content)
    //</editor-fold>
  }
}

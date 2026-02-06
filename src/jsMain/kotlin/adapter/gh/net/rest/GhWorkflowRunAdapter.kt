package adapter.gh.net.rest

import adapter.gh.net.RestClient
import adapter.gh.net.eTag
import adapter.gh.net.httpStatus
import adapter.gh.net.impl.GhRestClient.HttpHeaders
import adapter.gh.net.toJson
import adapter.gh.net.toResponseJson
import com.rnett.action.core.logger
import com.rnett.action.httpclient.HttpResponse
import domain.model.JobList
import domain.model.Result
import domain.model.RunConclusion
import domain.model.WorkflowRunList
import domain.model.RunStatus
import domain.model.WorkflowRun
import domain.ports.WorkflowRunsPort
import io.ktor.http.HttpStatusCode
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import utils.retryOnce
import kotlin.js.Date

class GhWorkflowRunAdapter(private val client: RestClient) : WorkflowRunsPort {

  override suspend fun retrieveWorkflowRuns(
    formerRunList: WorkflowRunList
  ): Result<WorkflowRunList> {
    val queryArgs = mapOf(
      queryEvent(), queryCreatedAt(formerRunList.dispatchTime), queryRef(formerRunList.ref)
    )
    val runsResponse = retryOnce {
      client.sendGet("actions/runs", queryArgs) {
        formerRunList.eTag?.also {
          this.add(HttpHeaders.IfNoneMatch, it)
        }
      }
    }

    return when (runsResponse.httpStatus()) {
      HttpStatusCode.OK -> {
        logger.info("Got workflow runs")

        val workflowRuns = extractWorkflowRuns(runsResponse, formerRunList.workflowId)
        Result.ok(
          formerRunList.copy(
            eTag = runsResponse.eTag(),
            runs = workflowRuns
          )
        )
      }

      HttpStatusCode.NotModified -> {
        logger.info("Run list up-to-date")
        Result.ok(formerRunList)
      }

      else -> Result.error("Unable to retrieve list of workflow runs! Details: ${runsResponse.toResponseJson()}")
    }
  }

  override suspend fun getRunDetails(run: WorkflowRun): Result<WorkflowRun> {
    val response = client.sendGet("actions/runs/${run.id}") {
      run.eTag?.also {
        this.add(HttpHeaders.IfNoneMatch, it)
      }
    }

    return when {
      HttpStatusCode.NotModified == response.httpStatus() -> Result.ok(run)

      !response.isSuccess() -> {
        val rawResp = response.toResponseJson(true)
        Result.error("Received error response while retrieving workflow run details! Response: $rawResp")
      }

      else -> {
        val runJson = response.toJson().jsonObject
        Result.ok(
          run.copy(
            eTag = response.eTag(),
            branch = getHeadBranch(runJson),
            status = getRunStatus(runJson)!!,
            conclusion = getConclusion(runJson),
            jobs = getJobs(runJson),
            dateCreated = getCreationDate(runJson)
          )
        )
      }
    }
  }

  private suspend fun extractWorkflowRuns(runsResponse: HttpResponse, workflowId: String) = runsResponse.toJson()
    .jsonObject
    .getValue("workflow_runs")
    .jsonArray
    .map { it.jsonObject }
    .filter { workflowId == getWorkflowId(it) }
    .map { WorkflowRun(getId(it)) }

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
    fun queryCreatedAt(at: Date) = QUERY_CREATED_AT to ">=${at.toISOString()}"
    fun queryRef(of: String) = QUERY_REF to of
    //</editor-fold>

    //<editor-fold desc="Json stuff">
    fun getWorkflowId(json: JsonObject) = json.getValue("workflow_id").jsonPrimitive.content
    fun getId(json: JsonObject) = json.getValue("id").jsonPrimitive.content
    fun getHeadBranch(json: JsonObject) = json.getValue("head_branch").jsonPrimitive.content
    fun getRunStatus(json: JsonObject) = RunStatus.from(json.getValue("status").jsonPrimitive.content)
    fun getConclusion(json: JsonObject) = RunConclusion.from(json.getValue("conclusion").jsonPrimitive.contentOrNull)
    fun getCreationDate(json: JsonObject) = json.getValue("created_at").jsonPrimitive.content
    fun getJobs(json: JsonObject) = JobList(json.getValue("jobs_url").jsonPrimitive.content)
    //</editor-fold>
  }
}

package usecases

import com.rnett.action.httpclient.HttpResponse
import data.GhRestClient
import data.WsClient.Companion.HEADER_IF_NONE_MATCH
import data.etag
import data.toJson
import data.toResponseJson
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import model.Jobs
import model.RunConclusion
import model.RunStatus
import model.WorkflowRun
import utils.actions.ActionFailedException

class WorkflowRuns(private val client: GhRestClient) {

  private val runs = mutableListOf<WorkflowRun>()

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
}
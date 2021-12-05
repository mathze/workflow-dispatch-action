import com.rnett.action.core.logger
import com.rnett.action.core.outputs
import data.GhGraphClient
import data.GhRestClient
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.uuid.nextUUID
import model.Inputs
import model.Inputs.Companion.resolveInputs
import usecases.WorkflowRuns
import usecases.Workflows
import utils.actions.ActionEnvironment
import utils.actions.ActionFailedException
import utils.failOrError
import utils.runAction
import kotlin.random.Random

suspend fun main(): Unit = runAction(
  before = ::resolveInputs,
  catch = ::catchException
) { inputs: Inputs ->
  if (inputs.token.isBlank()) {
    throw ActionFailedException("Unable to retrieve a token!")
  }

  if (inputs.ref.isNullOrBlank()) {
    logger.info("No branch given detecting default branch")
    val defaultBranch = detectDefaultBranch(inputs)
    inputs.ref = defaultBranch
  }

  if ((null == inputs.workflowName) && (null == inputs.runId)) {
    throw ActionFailedException("Either workflow-name or run-id must be set!")
  }

  val externalRunId = if (inputs.useIdentifierStep) {
    // generate external_run_id
    val uuid = Random.Default.nextUUID()
    val extRunId = "${ActionEnvironment.GITHUB_RUN_ID}-${ActionEnvironment.GITHUB_JOB}-$uuid"
    logger.info("Using external_ref_id: $extRunId")
    inputs.payload = JsonObject(inputs.payload.toMutableMap().also {
      it["external_ref_id"] = JsonPrimitive(extRunId)
    })
    extRunId
  } else null

  val client = GhRestClient(inputs.token, inputs.owner, inputs.repo)
  inputs.workflowName?.let {
    logger.info("Going to trigger workflow run.")
    val workflows = Workflows(client)
    val wfId = workflows.getWorkflowIdFromName(inputs.workflowName)
    logger.info("Got workflow-id $wfId for workflow ${inputs.workflowName}")
    val dispatchTime = workflows.triggerWorkflow(wfId, inputs.ref!!, inputs.payload)

    val wfRuns = WorkflowRuns(client)
    val workflowRun = wfRuns.waitForWorkflowRunCreated(
      wfId, dispatchTime, inputs.ref!!,
      inputs.triggerTimeout, inputs.triggerInterval,
      externalRunId
    )

    if (null == workflowRun) {
      failOrError("Unable to receive workflow run within ${inputs.triggerTimeout}!", inputs.failOnError)
    } else {
      logger.notice("Found workflow run with ${workflowRun.id}")
      outputs["run-id"] = workflowRun.id

      // we are in trigger and wait mode
      if (null != inputs.runId) {
        inputs.runId = workflowRun.id
      }
    }
  }

  inputs.runId?.let { runId ->
    logger.info("Going to wait until run $runId completes")
    val wfRun = WorkflowRuns(client)
    wfRun.waitWorkflowRunCompleted(runId, inputs.waitTimeout, inputs.waitInterval)
  }
}

fun catchException(inputs: Inputs, ex: Throwable) {
  failOrError(ex.message ?: "Error while trigger workflow", inputs.failOnError)
}


suspend fun detectDefaultBranch(inputs: Inputs): String {
  val ghClient = GhGraphClient(inputs.token)
  val owner = inputs.owner
  val repo = inputs.repo
  val request = """{
    repository(owner: "$owner", name: "$repo") {
      defaultBranchRef {
        name
      }
    }
  }""".trimIndent()

  return logger.withGroup("Retrieve default branch") {
    val response = ghClient.sendQuery(request).jsonObject
    val data = response["data"]!!.jsonObject
    val result = data["repository"]!!.jsonObject["defaultBranchRef"]!!.jsonObject["name"]!!.jsonPrimitive.content
    logger.info("Detected branch '$result' as default branch of '$owner/$repo'")
    result
  }
}

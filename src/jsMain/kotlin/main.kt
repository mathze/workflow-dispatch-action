import app.softwork.uuid.nextUuid
import com.rnett.action.core.logger
import com.rnett.action.core.outputs
import data.GhGraphClient
import data.GhRestClient
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import model.Inputs
import model.Inputs.Companion.resolveInputs
import usecases.WorkflowRuns
import usecases.Workflows
import utils.actions.ActionEnvironment
import utils.actions.ActionFailedException
import utils.failOrError
import kotlin.random.Random
import kotlin.uuid.ExperimentalUuidApi

suspend fun main() {
  // By design, errors in processing the inputs always make the action failing!
  val inputs: Inputs = resolveInputs()
  processAndValidateInputs(inputs)

  // Main action lifecycle
  try {
    // prepare the external reference id if requested
    val externalRefId = if (inputs.useIdentifierStep) {
      val extRunId = generateExternalRefId()
      logger.info("Using external_ref_id: $extRunId")
      inputs.payload = JsonObject(inputs.payload.toMutableMap().also {
        it["external_ref_id"] = JsonPrimitive(extRunId)
      })
      extRunId
    } else null

    val client = GhRestClient(inputs.token, inputs.owner, inputs.repo)
    if (null != inputs.workflowName) {
      val runId = processTriggerMode(client, inputs, externalRefId)
      // we are in 'trigger and wait' mode
      if (null != inputs.runId) {
        inputs.runId = runId
      }
    }

    inputs.runId?.let { runId ->
      logger.info("Going to wait until run $runId completes")
      val wfRun = WorkflowRuns(client)
      val result = wfRun.waitWorkflowRunCompleted(runId, inputs.waitTimeout, inputs.waitInterval)
      val run = result.second
      outputs["run-status"] = run.status.value
      outputs["run-conclusion"] = run.conclusion?.value ?: ""
      if (!result.first) {
        throw ActionFailedException("Triggered workflow does not complete within ${inputs.waitTimeout}!")
      }
    }

    outputs["failed"] = "false"
  } catch (ex: Throwable) {
    failOrError(ex.message ?: "Error while trigger workflow", inputs.failOnError)
  }
}

private suspend fun processAndValidateInputs(inputs: Inputs) {
  if (inputs.token.isBlank()) {
    throw ActionFailedException("Token must not be empty or blank!")
  }

  if ((null == inputs.workflowName) && (null == inputs.runId)) {
    throw ActionFailedException("Either workflow-name or run-id must be set!")
  }

  if (inputs.ref.isNullOrBlank()) {
    logger.info("No branch given, detecting default branch")
    val defaultBranch = detectDefaultBranch(inputs)
    inputs.ref = defaultBranch
  }
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

@OptIn(ExperimentalUuidApi::class)
private fun generateExternalRefId(): String = Random.Default.nextUuid().let {
  "${ActionEnvironment.GITHUB_RUN_ID}-${ActionEnvironment.GITHUB_JOB}-$it"
}

/**
 * Triggers the workflow dispatch event and tries to receive its workflow run id.
 * 
 * @param[client] Client to send the requests.
 * @param[inputs] The inputs passed to the action.
 * @param[externalReferenceId] The id used to pass to the target workflow (in case it isn't `null`).
 * 
 * @return The id of the workflow run.
 * @throws ActionFailedException in case we couldn't receive an id within the [Inputs.triggerTimeout].
 */
private suspend fun processTriggerMode(client: GhRestClient, inputs: Inputs, externalReferenceId: String?): String {
  logger.info("Going to trigger workflow run.")
  val workflows = Workflows(client)
  val wfId = workflows.getWorkflowIdFromName(inputs.workflowName!!)
  logger.info("Got workflow-id $wfId for workflow ${inputs.workflowName}")
  val dispatchTime = workflows.triggerWorkflow(wfId, inputs.ref!!, inputs.payload)

  val wfRuns = WorkflowRuns(client)
  val workflowRun = wfRuns.waitForWorkflowRunCreated(
    wfId, dispatchTime, inputs.ref!!,
    inputs.triggerTimeout, inputs.triggerInterval,
    externalReferenceId
  )

  return workflowRun?.let {
    logger.notice("Found workflow run with ${workflowRun.id}")
    outputs["run-id"] = workflowRun.id
    workflowRun.id
  } ?: throw ActionFailedException("Unable to receive workflow run within ${inputs.triggerTimeout}!")
}

package domain.usecases

import app.softwork.uuid.nextUuid
import com.rnett.action.core.logger
import com.rnett.action.core.outputs
import domain.model.ActionInputs
import domain.model.Result
import domain.ports.JobsPort
import domain.ports.WorkflowPort
import domain.ports.WorkflowRunsPort
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import utils.actions.ActionEnvironment
import utils.actions.ActionFailedException
import kotlin.js.Date
import kotlin.random.Random
import kotlin.uuid.ExperimentalUuidApi

class TriggerWorkflowRunUC(
  wfRunsPort: WorkflowRunsPort,
  jobsPort: JobsPort,
  private val wfPort: WorkflowPort
) {

  private val wfRunService = WorkflowRunsService(wfRunsPort, jobsPort)

  /**
   * Triggers the workflow and returns its *RunId*.
   *
   * @throws ActionFailedException in case we couldn't receive an id within the [ActionInputs.triggerWorkflowRunPollConfig].
   */
  suspend fun process(actionInputs: ActionInputs) : String {
    // prepare the external reference id if requested
    var payload = actionInputs.payload
    val externalRefId = if (actionInputs.useIdentifierStep) {
      val extRunId = generateExternalReferenceId()
      logger.info("Using external_ref_id: $extRunId")
      payload = JsonObject(actionInputs.payload.toMutableMap().also {
        it["external_ref_id"] = JsonPrimitive(extRunId)
      })
      extRunId
    } else null

    // get the technical id for the workflow name
    val wfId = getWorkflowIdFromName(actionInputs.workflowName!!)
    logger.info("Got workflow-id $wfId for workflow ${actionInputs.workflowName}")

    logger.info("Going to trigger workflow run.")
    // trigger a workflow run
    val dispatchTime = triggerWorkflow(wfId, actionInputs.ref!!, payload)

    // wait for workflow run to be created
    val workflowRun = wfRunService.waitForWorkflowRunCreated(
      workflowId = wfId,
      dispatchTime = dispatchTime,
      ref = actionInputs.ref!!,
      pollingConfig = actionInputs.triggerWorkflowRunPollConfig,
      externalRefId = externalRefId
    )

    return workflowRun?.let {
      logger.notice("Found workflow run with ${it.id}")
      outputs["run-id"] = it.id
      it.id
    } ?: throw ActionFailedException("\"Unable to receive workflow run within ${actionInputs.triggerWorkflowRunPollConfig.timeout}!")
  }

  /**
   * This id is used in the 'with marker step' scenario.
   * It enables the
   */
  @OptIn(ExperimentalUuidApi::class)
  private fun generateExternalReferenceId() = Random.Default.nextUuid().let {
    "${ActionEnvironment.GITHUB_RUN_ID}-${ActionEnvironment.GITHUB_JOB}-$it"
  }

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
  suspend fun getWorkflowIdFromName(wfName: String) = when (val wfIdResult = wfPort.retrieveWorkflowId(wfName)) {
    is Result.Ok -> wfIdResult.value
    is Result.Error -> throw ActionFailedException(wfIdResult.errorMessage)
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
  suspend fun triggerWorkflow(workflowId: String, ref: String, inputs: JsonObject? = null): Date =
    logger.withGroup("Triggering workflow") {
      when(val triggerDateResult = wfPort.triggerWorkflow(workflowId, ref, inputs)) {
        is Result.Ok -> triggerDateResult.value
        is Result.Error -> throw ActionFailedException(triggerDateResult.errorMessage)
      }
    }
}

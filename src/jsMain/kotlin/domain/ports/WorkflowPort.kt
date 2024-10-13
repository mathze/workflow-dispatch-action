package domain.ports

import domain.model.Result
import kotlinx.serialization.json.JsonObject
import kotlin.js.Date

interface WorkflowPort {

  /**
   * Retrieves the technical id of a workflow.
   *
   * Internally the action, as well as GitHub's apis, rely on ids rather than on "human-readable" names.
   *
   * @param workflowName Human-readable name of a workflow.
   *
   * @return GitHub's technical id for the workflow name or an error describing the issue.
   */
  suspend fun retrieveWorkflowId(workflowName: String): Result<String>

  /**
   * Fires the workflow dispatch event for a workflow.
   *
   * @param workflowId The `id` of the workflow.
   * @param ref The name of the `branch` the workflow shall run on.
   * @param inputs Additional data that will be sent as `inputs` to the workflow.
   *
   * @return The datetime of creation time (or current datetime if not received by the endpoint)
   */
  suspend fun triggerWorkflow(workflowId: String, ref: String, inputs: JsonObject? = null) : Result<Date>
}

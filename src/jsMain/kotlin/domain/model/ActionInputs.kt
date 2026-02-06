package domain.model

import kotlinx.serialization.json.JsonObject

data class ActionInputs(
  /**
   * The user or organization the workflow to trigger belongs to.
   * @see [repo]
   */
  val owner: String,
  /**
   * The repository the workflow to trigger belongs to.
   * @see [owner]
   */
  val repo: String,
  /**
   * Reference to the branch or tag of the workflow that shall be triggered.
   */
  var ref: String?,
  /**
   * The *file* name of the workflow that shall be triggered.
   */
  val workflowName: String?,
  /**
   * Additional payload that shall be sent to the triggered workflow.
   */
  val payload: JsonObject,
  /**
   * GH-Token to authorize against the api.
   */
  val token: String,
  /**
   * Whether the action should fail the workflow on error or not.
   */
  val failOnError: Boolean = false,
  /**
   * Whether to use an identifier step.
   *
   * Used for trigger phase.
   */
  val useIdentifierStep: Boolean,
  /**
   * ID of the workflow run to wait for.
   *
   * Used for wait phase.
   */
  var runId: String? = null,
  /**
   * Polling configuration used during `trigger workflow` phase.
   */
  val triggerWorkflowRunPollConfig: PollingConfig,
  /**
   * Polling configuration used during `wait for workflow run` phase.
   */
  val waitForRunPollConfig: PollingConfig
)

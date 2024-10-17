package domain.ports

import domain.model.Result
import domain.model.WorkflowRunList
import domain.model.WorkflowRun

interface WorkflowRunsPort {

  suspend fun retrieveWorkflowRuns(formerRunList: WorkflowRunList): Result<WorkflowRunList>

  /**
   * Retrieve the details of a workflow run from the GitHub's rest-api.
   *
   * The request uses the [WorkflowRun.id] and if present the respective [WorkflowRun.eTag].
   *
   * @param run The [WorkflowRun] for which to get the details.
   * @return A new [WorkflowRun] with updated values.
   */
  suspend fun getRunDetails(run: WorkflowRun): Result<WorkflowRun>
}

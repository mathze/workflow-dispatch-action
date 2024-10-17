package domain.usecases

import com.rnett.action.core.logger
import com.rnett.action.core.outputs
import domain.model.ActionInputs
import domain.ports.JobsPort
import domain.ports.WorkflowRunsPort
import utils.actions.ActionFailedException

class AwaitWorkflowRunFinishedUC(wfRunsPort: WorkflowRunsPort, jobsPort: JobsPort) {

  private val wfRun = WorkflowRunsService(wfRunsPort, jobsPort)

  suspend fun process(runId: String, actionInputs: ActionInputs) {
    logger.info("Going to wait until run $runId completes")
    val waitTimeout = actionInputs.waitForRunPollConfig.timeout
    val waitInterval = actionInputs.waitForRunPollConfig.interval
    val result = wfRun.waitWorkflowRunCompleted(runId, waitTimeout, waitInterval)
    val run = result.second
    outputs["run-status"] = run.status.value
    outputs["run-conclusion"] = run.conclusion?.value ?: ""
    if (!result.first) {
      val timeout = actionInputs.waitForRunPollConfig.timeout
      throw ActionFailedException("Triggered workflow does not complete within $timeout!")
    }
  }
}

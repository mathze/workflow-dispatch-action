import adapter.gh.net.graphql.GhRepoAdapter
import com.rnett.action.core.outputs
import adapter.gh.net.impl.GhGraphClient
import adapter.gh.net.impl.GhRestClient
import adapter.gh.net.rest.GhJobAdapter
import adapter.gh.net.rest.GhWorkflowAdapter
import adapter.gh.net.rest.GhWorkflowRunAdapter
import com.rnett.action.core.inputs
import domain.usecases.AwaitWorkflowRunFinishedUC
import domain.usecases.RetrieveAndValidateActionInputsUC
import domain.usecases.TriggerWorkflowRunUC
import utils.failOrError

suspend fun main() {

  // By design, errors in processing the inputs always make the action failing!
  val actionInputs = RetrieveAndValidateActionInputsUC().process(inputs) { token ->
    GhRepoAdapter(GhGraphClient(token))
  }

  // Main action lifecycle
  try {

    val client = GhRestClient(actionInputs.token, actionInputs.owner, actionInputs.repo)
    val wfPort = GhWorkflowAdapter(client)
    val jobsPort = GhJobAdapter(client)
    val wfRunsPort = GhWorkflowRunAdapter(client)
    val triggerWorkflowRunUseCase = TriggerWorkflowRunUC(wfRunsPort, jobsPort, wfPort)
    val awaitWorkflowRunFinishedUseCase = AwaitWorkflowRunFinishedUC(wfRunsPort, jobsPort)

    // we are in trigger mode
    if (null != actionInputs.workflowName) {
      val runId = triggerWorkflowRunUseCase.process(actionInputs)
      // we are in 'trigger and wait' mode
      if (null != actionInputs.runId) {
        actionInputs.runId = runId
      }
    }

    actionInputs.runId?.let { runId ->
      awaitWorkflowRunFinishedUseCase.process(runId, actionInputs)
    }

    outputs["failed"] = "false"
  } catch (ex: Throwable) {
    failOrError(ex, actionInputs.failOnError)
  }
}

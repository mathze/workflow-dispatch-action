package domain.usecases

import com.rnett.action.core.logger
import domain.model.PollingConfig
import domain.model.Result
import domain.model.RunStatus
import domain.model.WorkflowRun
import domain.model.WorkflowRunList
import domain.ports.JobsPort
import domain.ports.WorkflowRunsPort
import utils.actions.ActionFailedException
import utils.executePolling
import kotlin.js.Date
import kotlin.time.Duration

/**
 *
 * Class to deal with the main use cases trough GitHub's `runs` resource api.
 *
 * @constructor Create an instance to interact with GitHub's runs api.
 *
 * @param[wfRunsPort] An implementation of the port.
 * @param[jobsPort] An implementation of the port.
 */
class WorkflowRunsService(
  private val wfRunsPort: WorkflowRunsPort,
  private val jobsPort: JobsPort,
) {

  /**
   * Waits until a workflow run with given criteria exists.
   * If more than one run was found the first will be returned.
   * If the [externalRefId] is given, the found runs will be checked, no matter if only one was found or more!
   *
   * @param workflowId The id or name of the workflow the run belongs to.
   * @param dispatchTime The time the run was created.
   * @param ref The branch or tag the run belongs to.
   * @param pollingConfig Config used for waiting for the workflow to be created.
   * @param externalRefId The id used to check for in step-names. May be `null` if not marker step is used.
   *
   * @return The found workflow run or `null` if none was found within the timeout.
   */
  suspend fun waitForWorkflowRunCreated(
    workflowId: String,
    dispatchTime: Date,
    ref: String,
    pollingConfig: PollingConfig,
    externalRefId: String? = null
  ): WorkflowRun? = logger.withGroup("Trying to detect workflow run id") {

    var workflowRuns = WorkflowRunList(workflowId, ref, dispatchTime)
    val result: Pair<Boolean, WorkflowRun?> = executePolling(
      pollingConfig.timeout,
      pollingConfig.interval
    ) {
      workflowRuns = findWorkflowRun(workflowRuns)

      val wfRunListAndCandidate = findRunCandidateOrNull(workflowRuns, externalRefId)
      workflowRuns = wfRunListAndCandidate.first
      val runCandidate = wfRunListAndCandidate.second

      Pair(null != runCandidate, runCandidate)
    }

    return result.second
  }

  /**
   * Finds a workflow run matching the given criteria.
   *
   * @param[oldWfRunList] The WorkflowRunList from a previews call (to leverage etags & prevent web requests)
   * or a minimal initialized WorkflowRunList for first call.
   *
   * @return A new WorkflowRunList with updated run details.
   */
  private suspend fun findWorkflowRun(oldWfRunList: WorkflowRunList): WorkflowRunList {
    // 1. retrieve (updated) list of workflow runs
    val newWfRunList = when (val workflowRunsResult = wfRunsPort.retrieveWorkflowRuns(oldWfRunList)) {
      is Result.Ok -> workflowRunsResult.value
      is Result.Error -> throw ActionFailedException(workflowRunsResult.errorMessage)
    }
    logger.debug("Fetched ${newWfRunList.runs.size} workflow runs")

    // 2. create unified list of old (enriched) and new (plain just id) runs
    val unifiedRuns = unifyWorkflowRuns(oldWfRunList.runs, newWfRunList.runs)

    // 3. update details of the runs
    val updatedRuns = unifiedRuns
      .map { run -> Pair(run, wfRunsPort.getRunDetails(run)) }
      .map { p ->
        when (val updatedRunResult = p.second) {
          is Result.Ok -> updatedRunResult.value
          is Result.Error -> p.first.also { oldRun ->
            logger.warning("Retrieving run details for $oldRun failed! Details: \n${updatedRunResult.errorMessage}")
          }
        }
      }
    logger.info("Current runs in scope: $updatedRuns")

    return newWfRunList.copy(runs = updatedRuns)
  }

  /**
   * Finds the run that was triggered by the action. Therefor select from two strategies:
   *  1. External Reference ID is present: We search for that run which contains a step with an appropriate name.
   *  2. No External Reference ID present: We take the run that is closes after the dispatch date of the trigger
   *     workflow event.
   *
   * @param[workflowRunList] The list of workflow runs in scope.
   * @param[externalRefId] Optional external reference id used to find the right workflow run.
   *
   * @return A pair with a possible updated WorkflowRunList in `first` place and the found workflow run or
   * `null`, if no appropriate candidate was found, in second place.
   */
  private suspend fun findRunCandidateOrNull(
    workflowRunList: WorkflowRunList,
    externalRefId: String?
  ): Pair<WorkflowRunList, WorkflowRun?> {
    return if (null == externalRefId) {
      // we do not have an external reference id so we need to guess;
      // which is the closest to dispatch date
      Pair(workflowRunList, workflowRunList.runs.sortedWith(compareBy { it.dateCreated }).firstOrNull())
    } else {
      // we have an external reference id, so we search for a run with a corresponding step name
      // 1. update the jobs within each run
      val updatedRuns = workflowRunList.runs.map {
        when (val updatedJobsResult = jobsPort.fetchJobs(it.jobs)) {
          is Result.Ok -> it.copy(jobs = updatedJobsResult.value)
          is Result.Error -> it
        }
      }

      // 2. find the run with job.step name
      val candidate = updatedRuns.filter(this::runCanHaveSteps)
        .firstOrNull { run -> run.jobs.hasJobWithStepName(externalRefId) }

      // 3. create an updated workflow run list and the candidate
      Pair(workflowRunList.copy(runs = updatedRuns), candidate)
    }
  }

  /**
   * Check if a workflow run is in a state that can have steps.
   * E.g. a `run` in a state of `QUEUED` can not have any jobs or steps as the run wasn't started jet.
   */
  private fun runCanHaveSteps(run: WorkflowRun) = when (run.status) {
    RunStatus.IN_PROGRESS, RunStatus.COMPLETED -> true
    RunStatus.QUEUED, RunStatus.PENDING, RunStatus.REQUESTED, RunStatus.WAITING -> false
  }

  /**
   * Create the current known run list by removing obsolete old runs and adding unknown new one.
   *
   * We do not simply replace known runs with the fresh ones, because we already have requested the run details
   * and therefor also have an eTag which spare the users rate limit.
   *
   * Flow:
   * - First, determines runs that were created since last query (in first run all are new).
   * - Second and last, we remove those runs that no longer exists (edge-case if runs where deleted in meantime).
   *
   * @param[oldRuns] A mutable list of already requested [WorkflowRun]s.
   * @param[newRuns] The list of prefiltered 'run' used to update list of already requested [WorkflowRun]s (refer to [oldRuns]).
   */
  private fun unifyWorkflowRuns(oldRuns: List<WorkflowRun>, newRuns: List<WorkflowRun>): List<WorkflowRun> {
    val newRuns = newRuns.filter { newRun ->
      oldRuns.none { oldRun -> oldRun.id == newRun.id }
    }
    logger.info("Found ${newRuns.size} new runs")

    val removedRuns = oldRuns.filter { oldRun ->
      newRuns.none { newRun -> newRun.id == oldRun.id }
    }
    logger.info("Found ${removedRuns.size} removed runs")

    return oldRuns.toMutableList().apply {
      removeAll(removedRuns)
      addAll(newRuns)
    }
  }

  /**
   * Waits until a workflow run reaches the [complete][RunStatus.COMPLETED] status or the timeout exceeds.
   *
   * Waiting will be performed in a loop using etag to spare users rate limit.
   *
   * @param[workflowRunId] The id of the workflow run for which to wait.
   * @param[maxTimeout] The maximum duration this function shall wait on the status before 'giving up'.
   * @param[frequency] The duration to wait between two consecutive requests.
   *
   * @return Pair where the first member indicates the success and the second member contains the WorkflowRun.
   * @throws ActionFailedException In case updating run details fails.
   * @see [executePolling]
   */
  suspend fun waitWorkflowRunCompleted(
    workflowRunId: String,
    maxTimeout: Duration,
    frequency: Duration
  ): Pair<Boolean, WorkflowRun> {
    var runDetails = WorkflowRun(id = workflowRunId)
    return executePolling(maxTimeout, frequency) {
      when (val newRunDetails = wfRunsPort.getRunDetails(runDetails)) {
        is Result.Ok -> {
          runDetails = newRunDetails.value
          Pair(runDetails.status == RunStatus.COMPLETED, runDetails)
        }

        is Result.Error -> {
          throw ActionFailedException(newRunDetails.errorMessage)
        }
      }
    }
  }
}

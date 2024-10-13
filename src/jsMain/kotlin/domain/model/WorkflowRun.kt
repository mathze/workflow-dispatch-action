package domain.model

data class WorkflowRun(
  val id: String,
  val eTag: String? = null,
  val branch: String? = null,
  val status: RunStatus = RunStatus.QUEUED,
  val conclusion: RunConclusion? = null,
  val jobs: JobList = JobList.EMPTY,
  val dateCreated: String? = null
)

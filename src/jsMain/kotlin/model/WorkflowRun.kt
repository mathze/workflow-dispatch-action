package model

data class WorkflowRun(
  val id: String,
  var etag: String? = null,
  var branch: String? = null,
  var status: RunStatus = RunStatus.QUEUED,
  var conclusion: RunConclusion? = null,
  var jobs: Jobs? = null,
  var dateCreated: String? = null
)

enum class RunStatus(val value: String) {
  QUEUED("queued"),
  PENDING("pending"),
  IN_PROGRESS("in_progress"),
  COMPLETED("completed"),
  REQUESTED("requested"),
  WAITING("waiting");

  companion object {
    fun from(value: String?) = value?.let { v ->
      values().firstOrNull {
        it.value == v
      } ?: throw IllegalArgumentException("Cannot map unknown value '$v' to RunStatus!")
    }
  }
}

enum class RunConclusion(val value: String) {
  ACTION_REQUIRED("action_required"),
  CANCELLED("cancelled"),
  FAILURE("failure"),
  NEUTRAL("neutral"),
  SUCCESS("success"),
  SKIPPED("skipped"),
  STALE("stale"),
  TIMED_OUT("timed_out");

  companion object {
    fun from(value: String?) = value?.let { v ->
      values().firstOrNull {
        it.value == v
      } ?: throw IllegalArgumentException("Cannot map unknown value '$v' to RunConclusion!")
    }
  }
}

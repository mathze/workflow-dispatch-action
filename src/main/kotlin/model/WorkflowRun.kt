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

enum class RunStatus(private val value: String) {
  QUEUED("queued"),
  IN_PROGRESS("in_progress"),
  COMPLETED("completed"),
  REQUESTED("requested"),
  WAITING("waiting");

  fun match(restState: String): Boolean = value == restState

  companion object {
    fun from(value: String?) = value?.let {
      values().first {
        it.match(value)
      }
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
    fun from(value: String?) = value?.let {
      values().firstOrNull {
        it.value == value
      }
    }
  }
}
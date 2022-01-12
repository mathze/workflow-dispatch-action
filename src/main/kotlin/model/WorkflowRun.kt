package model

data class WorkflowRun(
  val id: String,
  var etag: String?,
  val branch: String,
  var status: RunStatus,
  var conclusion: RunConclusion?
)

enum class RunStatus(val value: String) {
  QUEUED("queued"),
  IN_PROGRESS("in_progress"),
  COMPLETED("completed");

  companion object {
    fun from(value: String) = values().first {
      it.value == value
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
    fun from(value: String) = values().firstOrNull {
      it.value == value
    }
  }
}
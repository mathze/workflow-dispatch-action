package domain.model

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
      entries.firstOrNull {
        it.value == v
      } ?: throw IllegalArgumentException("Cannot map unknown value '$v' to RunConclusion!")
    }
  }
}

package domain.model

enum class RunStatus(val value: String) {
  QUEUED("queued"),
  PENDING("pending"),
  IN_PROGRESS("in_progress"),
  COMPLETED("completed"),
  REQUESTED("requested"),
  WAITING("waiting");

  companion object {
    fun from(value: String?) = value?.let { v ->
      entries.firstOrNull {
        it.value == v
      } ?: throw IllegalArgumentException("Cannot map unknown value '$v' to RunStatus!")
    }
  }
}

package domain.model

data class JobList(
  val url: String,
  val eTag: String? = null,
  val jobs: List<Job> = listOf()
) {

  fun hasJobWithStepName(name: String): Boolean = jobs.any { job ->
    job.steps.any { step ->
      name == step.name
    }
  }

  data class Job(val steps: List<Step>)

  data class Step(val name: String)

  companion object {
    val EMPTY = JobList("")
  }
}

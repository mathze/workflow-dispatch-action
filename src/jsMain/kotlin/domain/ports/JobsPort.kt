package domain.ports

import domain.model.JobList
import domain.model.Result

interface JobsPort {

  suspend fun fetchJobs(jobs: JobList): Result<JobList>
}

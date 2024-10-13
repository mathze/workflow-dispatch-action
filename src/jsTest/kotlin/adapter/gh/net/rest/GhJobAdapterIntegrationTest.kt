package adapter.gh.net.rest

import domain.model.JobList
import domain.model.Result
import withMockServer
import kotlin.test.Test
import kotlin.test.assertContains
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertSame

class GhJobAdapterIntegrationTest : WebIntegrationTestBase() {

  val sut = GhJobAdapter(restClient)

  @Test
  fun should_fetch_and_map_jobList() = withMockServer {
    // given // when
    val res = sut.fetchJobs(JobList("jobs/42"))

    // then
    val value = assertIs<Result.Ok<JobList>>(res).value
    assertEquals("new eTag", value.eTag)
    assertEquals(1, value.jobs.size, "Unexpected number of jobs")
    val job = value.jobs.first()
    assertEquals(1, job.steps.size, "Unexpected number of steps")
    val step = job.steps.first()
    assertEquals("dummy", step.name, "Unexpected step name")
  }

  @Test
  fun should_handle_not_modified_and_return_initial_jobList() = withMockServer {
    // given
    val jobList = JobList("jobs/13", "Tag13")

    // when
    val res = sut.fetchJobs(jobList)

    // then
    val value = assertIs<Result.Ok<JobList>>(res).value
    assertSame(jobList, value)
  }

  @Test
  fun should_handle_errors() = withMockServer {
    // given
    val url = "jobs/error"

    // when
    val res = sut.fetchJobs(JobList(url))

    // then
    val value = assertIs<Result.Error>(res).errorMessage
    assertContains(value, "Cannot retrieve jobs from $url! Details:")
  }
}

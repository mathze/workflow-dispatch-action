package adapter.gh.net.rest

import adapter.gh.net.RestClient
import com.rnett.action.httpclient.HttpResponse
import dev.mokkery.answering.returns
import dev.mokkery.answering.sequentially
import dev.mokkery.answering.throwsErrorWith
import dev.mokkery.every
import dev.mokkery.everySuspend
import dev.mokkery.matcher.any
import dev.mokkery.mock
import domain.model.JobList
import domain.model.Result
import io.ktor.http.HttpStatusCode
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertIs
import kotlin.test.assertSame

class GhJobAdapterUnitTest {

  val client = mock<RestClient>()
  val sut = GhJobAdapter(client)

  @Test
  fun should_return_initial_JobList_if_not_modified() = runTest {
    // given
    val response = mock<HttpResponse>()
    every { response.statusCode } returns HttpStatusCode.NotModified.value
    everySuspend { client.sendGet(pathOrUrl = any(), headerProvider = any()) } returns response
    val jl = JobList("http://any.url")

    // when
    val result = sut.fetchJobs(jl)

    // then
    val resultValue = assertIs<Result.Ok<JobList>>(result).value
    assertSame(jl, resultValue)
  }

  @Test
  fun should_retry_once_on_exception() = runTest {
    // given
    val url = "jobs/timeout"
    everySuspend { client.sendGet(any(), any(), any()) } sequentially {
      throwsErrorWith("Timeout")
      val res = mock<HttpResponse>()
      every { res.statusCode } returns HttpStatusCode.NotModified.value
      returns(res)
    }
    val jobList = JobList(url)

    // when
    val res = sut.fetchJobs(jobList)

    // then
    val value = assertIs<Result.Ok<JobList>>(res).value
    assertSame(jobList, value)
  }
}

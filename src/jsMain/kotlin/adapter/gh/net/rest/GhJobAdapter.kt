package adapter.gh.net.rest

import adapter.gh.net.RestClient
import adapter.gh.net.eTag
import adapter.gh.net.httpStatus
import adapter.gh.net.impl.GhRestClient.HttpHeaders
import adapter.gh.net.toJson
import adapter.gh.net.toResponseJson
import com.rnett.action.core.logger
import domain.model.JobList
import domain.model.JobList.Job
import domain.model.JobList.Step
import domain.model.Result
import domain.ports.JobsPort
import io.ktor.http.HttpStatusCode
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import utils.retryOnce

class GhJobAdapter(private val client: RestClient) : JobsPort {

  override suspend fun fetchJobs(jobList: JobList): Result<JobList> {
    val response = retryOnce {
      client.sendGet(jobList.url) {
        jobList.eTag?.let {
          this.add(HttpHeaders.IfNoneMatch, it)
        }
      }
    }

    return when {
      HttpStatusCode.NotModified == response.httpStatus() -> {
        logger.debug("JobList: Not modified")
        Result.ok(jobList)
      }

      response.isSuccess() -> {
        val jobs = extractJobs(response.toJson().jsonObject.getValue("jobs").jsonArray)
        Result.ok(
          jobList.copy(
            jobs = jobs,
            eTag = response.eTag()
          )
        )
      }

      else -> Result.error("Cannot retrieve jobs from ${jobList.url}! Details:${response.toResponseJson(true)}")
    }
  }

  private fun extractJobs(jobsJson: JsonArray): List<Job> = jobsJson.map {
    Job(extractStepsFromJob(it.jsonObject))
  }

  private fun extractStepsFromJob(jobJson: JsonObject): List<Step> {
    return jobJson.getValue("steps")
      .jsonArray
      .mapNotNull {
        it.jsonObject.getValue("name").jsonPrimitive.contentOrNull
      }
      .map(::Step)
  }
}

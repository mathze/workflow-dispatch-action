package usecases

import data.GhRestClient
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlin.js.Date
import kotlin.math.abs
import kotlin.time.Duration
import kotlin.time.DurationUnit
import kotlin.time.toDuration

class Workflows(private val client: GhRestClient) {

  suspend fun findWorkflowId(wfName: String): String {
    val response = client.sendGet("actions/workflows/wfName")
    return response.jsonObject["id"]!!.jsonPrimitive.content
  }

  suspend fun waitForWorkflowStarted(workflowId: String, startTime: Date, maxTimeout: Duration): String? {
    val start = Date()

    var result: String? = null
    while ((null == result) && (Date().delta(start) < maxTimeout)) {
      result = findRunIdOf(workflowId, startTime)
    }

    return result
  }

  private suspend fun findRunIdOf(workflowId: String, startTime: Date): String? {
    val resonse = client.sendGet("")
    
    return null
  }

  private fun Date.delta(other: Date): Duration {
    val msThis = this.getMilliseconds()
    val msOther = other.getMilliseconds()
    val delta = abs(msThis - msOther)
    return delta.toDuration(DurationUnit.MILLISECONDS)
  }
}
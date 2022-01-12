import com.rnett.action.core.inputs.getOptional
import com.rnett.action.core.inputs.getOrElse
import com.rnett.action.core.inputs.getRequired
import com.rnett.action.core.logger
import com.rnett.action.core.maskSecret
import data.GhGraphClient
import data.GhRestClient
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import model.Inputs
import usecases.Workflows
import utils.actions.ActionEnvironment
import utils.failOrError
import utils.runAction
import kotlin.time.Duration.Companion.seconds

val MAX_WORKFLOW_RUN_WAIT = 10.seconds

suspend fun main(): Unit = runAction(
  before = ::resolveInputs,
  catch = ::catchException
) { inputs: Inputs ->
  if (inputs.ref.isNullOrBlank()) {
    logger.info("No branch given detecting default branch")
    val defaultBranch = detectDefaultBranch(inputs)
    inputs.ref = defaultBranch
  }

  val client = GhRestClient(inputs.token, inputs.owner, inputs.repo)
  val workflowRun = logger.withGroup("Triggering workflow") {
    val workflows = Workflows(client)
    val wfId = workflows.findWorkflowId(inputs.workflowName)
    logger.info("Got workflow-id $wfId for workflow ${inputs.workflowName}")
    val requestTime = workflows.triggerWorkflow(wfId, inputs.ref!!, inputs.payload)
    workflows.waitForWorkflowRunCreated(wfId, requestTime, inputs.ref!!, MAX_WORKFLOW_RUN_WAIT)
  }

  if (null == workflowRun) {
    failOrError("Unable to receive workflow run within $MAX_WORKFLOW_RUN_WAIT!", inputs.failOnError)
  } else {
    logger.notice("Found workflow run with ${workflowRun.id}")
  }
}

fun catchException(inputs: Inputs, ex: Throwable) {
  failOrError(ex.message ?: "Error while trigger workflow", inputs.failOnError)
}

fun resolveInputs() = logger.withGroup("Reading inputs") {
  val (currOwner, currRepo) = ActionEnvironment.GITHUB_REPOSITORY.split('/')
  Inputs(
    getOrElse("owner") { currOwner },
    getOrElse("repo") { currRepo },
    getOptional("ref"),
    getRequired("workflowname"),
    Json.parseToJsonElement(getOrElse("payload") { "{}" }).jsonObject,
    getRequired("token").apply { maskSecret() },
    getOptional("failOnError")?.toBooleanStrictOrNull() ?: false
  ).also { 
    logger.info("Got inputs: $it")
  }
}

suspend fun detectDefaultBranch(inputs: Inputs): String {
  val ghClient = GhGraphClient(inputs.token)
  val owner = inputs.owner
  val repo = inputs.repo
  val request = """{
    repository(owner: "$owner", name: "$repo") {
      defaultBranchRef {
        name
      }
    }
  }""".trimIndent()

  return logger.withGroup("Retrieve default branch") {
    val response = ghClient.sendQuery(request).jsonObject
    val data = response["data"]!!.jsonObject
    val result = data["repository"]!!.jsonObject["defaultBranchRef"]!!.jsonObject["name"]!!.jsonPrimitive.content
    logger.info("ℹ️ Detected branch '$result' as default branch of '$owner/$repo'")
    result
  }
}
import com.rnett.action.github.github
import data.GhGraphClient
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import model.Inputs
import utils.actions.ActionEnvironment
import utils.actions.getInput
import utils.actions.group
import utils.actions.info
import utils.failOrError
import utils.runAction

suspend fun main(): Unit = runAction(
  before = ::resolveInputs,
  catch = ::catchException
) { inputs ->
  if (inputs.ref.isNullOrBlank()) {
    val defaultBranch = detectDefaultBranch(inputs)
    inputs.ref = defaultBranch
  }
  
}

fun catchException(inputs: Inputs, ex: Throwable) {
  failOrError(ex.message ?: "Error while trigger workflow", inputs.failOnError)
}

fun resolveInputs() = group("Reading inputs") {
  val (currOwner, currRepo) = ActionEnvironment.GITHUB_REPOSITORY.split('/')
  return@group Inputs(
    getInput("owner").ifBlank { currOwner },
    getInput("repo").ifBlank { currRepo },
    getInput("ref").ifBlank { null },
    getInput("workflowname"),
    Json.parseToJsonElement(getInput("payload").ifBlank { "{}" }).jsonObject,
    getInput("token"),
    getInput("failOnError").toBooleanStrictOrNull() ?: false
  )
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

  return group("Retrieve default branch") {
    val response = ghClient.sendQuery(request).jsonObject
    val data = response["data"]!!.jsonObject
    val result = data["repository"]!!.jsonObject["defaultBranchRef"]!!.jsonObject["name"]!!.jsonPrimitive.toString()
    info("ℹ️ Detected branch '$result' as default branch of '$owner/$repo'")
    result
  }
}
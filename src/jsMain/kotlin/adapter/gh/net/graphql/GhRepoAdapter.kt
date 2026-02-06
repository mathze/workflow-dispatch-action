package adapter.gh.net.graphql

import adapter.gh.net.GraphQlClient
import adapter.gh.net.toJson
import adapter.gh.net.toResponseJson
import com.rnett.action.core.logger
import com.rnett.action.core.logger.debug
import domain.model.Result
import domain.ports.RepositoryPort
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

class GhRepoAdapter(private val gqlClient: GraphQlClient) : RepositoryPort {

  override suspend fun getDefaultBranch(owner: String, repository: String): Result<String> {
    val request = """
      query GetDefaultBranch(${'$'}owner: String!, ${'$'}name: String!) {
        repository(owner: ${'$'}owner, name: ${'$'}name) {
          defaultBranchRef {
            name
          }
        }
      }""".trimIndent()

    debug("Retrieving default branch name")
    val response = gqlClient.sendQuery(request, buildJsonObject {
      put("owner", JsonPrimitive(owner))
      put("name", JsonPrimitive(repository))
    })
    return when {

      response.isSuccess() -> response.toJson().let {
        debug("Got body: $it")
        val name = extractBranchName(it)
        if (name.isNullOrBlank()) {
          Result.error("Unable to retrieve default branch name from response! Body: $it")
        } else {
          Result.Ok<String>(name)
        }
      }

      else -> {
        logger.error("Request failed: ${response.toResponseJson()}")
        Result.error("Error while retrieving default branch name! See log for details")
      }
    }
  }

  private fun extractBranchName(rootNode: JsonElement): String? {
    val nameNode = rootNode.data()
      .repository()
      .defaultBranchRef()
      .name()
    return nameNode?.jsonPrimitive?.contentOrNull
  }

  private fun JsonElement?.data() = this?.jsonObject?.get("data")
  private fun JsonElement?.repository() = this?.jsonObject?.get("repository")
  private fun JsonElement?.defaultBranchRef() = this?.jsonObject?.get("defaultBranchRef")
  private fun JsonElement?.name() = this?.jsonObject?.get("name")
}

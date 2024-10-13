package adapter.gh.net.rest

import adapter.gh.net.impl.GhGraphClient
import adapter.gh.net.impl.GhRestClient
import kotlinx.js.set
import node.process.process
import kotlin.test.BeforeTest

abstract class WebIntegrationTestBase {
  protected open val owner = "owner"
  protected open val repo = "repo"
  protected val restClient = GhRestClient("token", owner, repo)
  protected val gqlClient = GhGraphClient("token")

  @BeforeTest
  fun setup() {
    val url = "http://localhost:8080"
    process.env["GITHUB_API_URL"] = url
    process.env["GITHUB_GRAPHQL_URL"] = "$url/gql"
  }
}

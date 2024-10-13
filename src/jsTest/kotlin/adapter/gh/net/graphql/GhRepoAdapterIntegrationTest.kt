package adapter.gh.net.graphql

import adapter.gh.net.rest.WebIntegrationTestBase
import domain.model.Result
import withMockServer
import kotlin.test.Test
import kotlin.test.assertContains
import kotlin.test.assertEquals
import kotlin.test.assertIs

class GhRepoAdapterIntegrationTest : WebIntegrationTestBase() {

  private val sut = GhRepoAdapter(gqlClient)

  @Test
  fun should_get_default_branch_name() = withMockServer {
    // when
    val res = sut.getDefaultBranch(owner, "ok")

    // then
    val value = assertIs<Result.Ok<String>>(res).value
    assertEquals("default branch", value)
  }

  @Test
  fun should_handle_parsing_issues_gracefully_no_name() = withMockServer {
    // when
    val res = sut.getDefaultBranch(owner, "resp not ok - no name")

    // then
    val value = assertIs<Result.Error>(res).errorMessage
    assertContains(value, "Unable to retrieve default branch name from response! Body:")
    assertContains(value, "\"nom\":\"default branch\"")
  }

  @Test
  fun should_handle_parsing_issues_gracefully_no_defaultBranchRef() = withMockServer {
    // when
    val res = sut.getDefaultBranch(owner, "resp not ok - no defaultBranchRef")

    // then
    val value = assertIs<Result.Error>(res).errorMessage
    assertContains(value, "Unable to retrieve default branch name from response! Body:")
    assertContains(value, "\"defaultBrunchRef\":{\"name\":")
  }

  @Test
  fun should_handle_parsing_issues_gracefully_no_repository() = withMockServer {
    // when
    val res = sut.getDefaultBranch(owner, "resp not ok - no repository")

    // then
    val value = assertIs<Result.Error>(res).errorMessage
    assertContains(value, "Unable to retrieve default branch name from response! Body:")
    assertContains(value, "\"data\":{\"repo\":{\"defaultBranchRef\"")
  }

  @Test
  fun should_handle_parsing_issues_gracefully_no_data() = withMockServer {
    // when
    val res = sut.getDefaultBranch(owner, "resp not ok - no data")

    // then
    val value = assertIs<Result.Error>(res).errorMessage
    assertContains(value, "Unable to retrieve default branch name from response! Body:")
    assertContains(value, "\"date\":{\"repository\":{\"defaultBranchRef\"")
  }

  @Test
  fun should_handle_response_issues_gracefully() = withMockServer {
    // when
    val res = sut.getDefaultBranch(owner, "resp not ok - error")

    // then
    val value = assertIs<Result.Error>(res).errorMessage
    assertContains(value, "Error while retrieving default branch name! See log for details")
  }

}

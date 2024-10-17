package adapter.gh.net.rest

import domain.model.Result
import domain.model.WorkflowRun
import domain.model.WorkflowRunList
import kotlinx.js.set
import node.process.process
import withMockServer
import kotlin.js.Date
import kotlin.test.Test
import kotlin.test.assertContains
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFails
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertSame

class GhWorkflowRunAdapterIntegrationTest : WebIntegrationTestBase() {

  val sut = GhWorkflowRunAdapter(restClient)

  @Test
  fun should_retrieve_workflow_runs_no_eTag() = withMockServer {
    // given
    val runList = WorkflowRunList(
      workflowId = "42",
      ref = "ok no eTag",
      dispatchTime = Date("2024-10-12 20:53:00Z"),
      eTag = "I got overridden"
    )

    // when
    val result = sut.retrieveWorkflowRuns(runList)

    // then
    val value = assertIs<Result.Ok<WorkflowRunList>>(result).value
    assertNull(value.eTag, "Should not have eTag")
    val runs = value.runs
    assertEquals(1, runs.size)
    val run = runs.first()
    assertEquals("13", run.id)
  }

  @Test
  fun should_retrieve_workflow_runs_with_new_eTag() = withMockServer {
    // given
    val runList = WorkflowRunList(
      workflowId = "42",
      ref = "ok new eTag",
      dispatchTime = Date("2024-10-12 20:53:00Z"),
      eTag = "I got overridden"
    )

    // when
    val result = sut.retrieveWorkflowRuns(runList)

    // then
    val value = assertIs<Result.Ok<WorkflowRunList>>(result).value
    assertEquals("new eTag", value.eTag)
    val runs = value.runs
    assertEquals(1, runs.size)
    val run = runs.first()
    assertEquals("13", run.id)
  }

  @Test
  fun should_only_retrieve_jobs_if_workflow_id_matches() = withMockServer {
    // given
    val runList = WorkflowRunList(
      workflowId = "42",
      ref = "multiple workflow runs",
      dispatchTime = Date("2024-10-12 20:53:00Z")
    )

    // when
    val response = sut.retrieveWorkflowRuns(runList)

    // then
    val result = assertIs<Result.Ok<WorkflowRunList>>(response).value
    val runs = result.runs
    assertEquals(2, runs.size)
    assertContentEquals(listOf("13", "19"), runs.map(WorkflowRun::id))
  }

  @Test
  fun should_keep_existing_workflow_runs_if_not_modified() = withMockServer {
    // given
    val runList = WorkflowRunList(
      workflowId = "42",
      ref = "not modified",
      dispatchTime = Date("2024-10-12 20:53:00Z"),
      eTag = "I stay the same",
      runs = listOf(
        WorkflowRun(id = "13")
      )
    )

    // when
    val result = sut.retrieveWorkflowRuns(runList)

    // then
    val value = assertIs<Result.Ok<WorkflowRunList>>(result).value
    assertSame(runList, value)
  }

  @Test
  fun should_throw_on_response_parsing_issues_no_workflow_id() = withMockServer {
    // given
    val runList = WorkflowRunList(
      workflowId = "42",
      ref = "parsing issue no workflow_id",
      dispatchTime = Date("2024-10-12 20:53:00Z")
    )

    // when
    val ex = assertFails { sut.retrieveWorkflowRuns(runList) }

    // then
    assertIs<NoSuchElementException>(ex)
    val message = assertNotNull(ex.message)
    assertContains(message, "workflow_id")
  }

  @Test
  fun should_throw_on_response_parsing_issues_no_workflow_runs() = withMockServer {
    // given
    val runList = WorkflowRunList(
      workflowId = "42",
      ref = "parsing issue no workflow_runs",
      dispatchTime = Date("2024-10-12 20:53:00Z")
    )

    // when
    val ex = assertFails { sut.retrieveWorkflowRuns(runList) }

    // then
    assertIs<NoSuchElementException>(ex)
    val message = assertNotNull(ex.message)
    assertContains(message, "workflow_runs")
  }

  @Test
  fun should_throw_on_response_parsing_issues_no_id() = withMockServer {
    // given
    val runList = WorkflowRunList(
      workflowId = "42",
      ref = "parsing issue no id",
      dispatchTime = Date("2024-10-12 20:53:00Z")
    )

    // when
    val ex = assertFails { sut.retrieveWorkflowRuns(runList) }

    // then
    assertIs<NoSuchElementException>(ex)
    val message = assertNotNull(ex.message)
    assertContains(message, "id")
  }

  @Test
  fun should_retry_on_network_issue() = withMockServer {
    // given
    process.env["GITHUB_API_URL"] = "http://localhost:9090"
    val runList = WorkflowRunList(
      workflowId = "64",
      ref = "parsing issue no id",
      dispatchTime = Date("2024-10-12 20:53:00Z")
    )

    // when
    val result = sut.retrieveWorkflowRuns(runList)

    // then
    val value = assertIs<Result.Ok<WorkflowRunList>>(result).value
    assertEquals("eTag 4 network issue", value.eTag)
    val runs = value.runs
    assertEquals(1, runs.size)
    val run = runs.first()
    assertEquals("99", run.id)
  }
}

package adapter.gh.net.rest

import domain.model.Result
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import withMockServer
import kotlin.js.Date
import kotlin.test.Test
import kotlin.test.assertContains
import kotlin.test.assertEquals
import kotlin.test.assertFails
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class GhWorkflowAdapterIntegrationTest : WebIntegrationTestBase() {

  val sut = GhWorkflowAdapter(restClient)

  @Test
  fun should_retrieve_id_from_workflow_name() = withMockServer {
    // given // when
    val res = sut.retrieveWorkflowId("test.yml")

    // then
    val value = assertIs<Result.Ok<String>>(res).value
    assertEquals("42", value)
  }

  @Test
  fun retrieve_workflow_id_should_handle_error_gracefully() = withMockServer {
    // given // when
    val res = sut.retrieveWorkflowId("asd")

    // then
    val value = assertIs<Result.Error>(res).errorMessage
    assertEquals("Unable to receive workflow id for 'asd'! Details see log", value)
  }

  @Test
  fun retrieve_workflow_id_should_fail_on_invalid_json() = withMockServer {
    // given // when
    val exMessage = assertFails { sut.retrieveWorkflowId("invalid.yml") }.message

    // then
    val msg = assertNotNull(exMessage)
    assertContains(msg, "Key id is missing")
  }

  @Test
  fun should_trigger_workflow_and_use_date_from_response() = withMockServer {
    // when
    val result = sut.triggerWorkflow(
      "11",
      "main",
      JsonObject(
        mapOf(
          "greeting" to JsonPrimitive("Hello world")
        )
      )
    )

    // then
    val value = assertIs<Result.Ok<Date>>(result).value
    assertEquals(Date("2029-11-05 13:07:42Z").getMilliseconds(), value.getMilliseconds())
  }

  @Test
  fun should_trigger_workflow_and_use_fallback_date() = withMockServer {
    // given
    val before = Date()

    // when
    val result = sut.triggerWorkflow(
      "13",
      "no_date"
    )

    // then
    val after = Date()
    val value = assertIs<Result.Ok<Date>>(result).value
    assertTrue("Expected date ($value) between $before and $after") {
      (before.getMilliseconds() <= value.getMilliseconds()) &&
          (value.getMilliseconds() <= after.getMilliseconds())

    }
  }

  @Test
  fun trigger_workflow_should_handle_error_gracefully() = withMockServer {
    // when
    val result = sut.triggerWorkflow(
      "7",
      "error"
    )

    // then
    val value = assertIs<Result.Error>(result).errorMessage
    assertEquals("Error starting workflow! For response details see log.", value)
  }
}

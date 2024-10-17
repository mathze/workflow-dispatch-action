package domain.usecases

import com.rnett.action.delegates.Delegatable
import dev.mokkery.answering.returns
import dev.mokkery.everySuspend
import dev.mokkery.matcher.any
import dev.mokkery.mock
import domain.model.ActionInputs
import domain.model.PollingConfig
import domain.model.Result
import domain.ports.RepositoryPort
import kotlinx.coroutines.test.runTest
import kotlinx.js.set
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import node.process.process
import utils.actions.ActionFailedException
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFails
import kotlin.test.assertIs
import kotlin.time.Duration.Companion.hours
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Duration.Companion.seconds

class RetrieveAndValidateActionInputsUCUnitTest {

  private val sut = RetrieveAndValidateActionInputsUC()
  private val repoPortMock = mock<RepositoryPort>()

  @Test
  fun should_fail_if_token_missing() = runTest {
    // given
    val inputs = createInputMock()

    // when // then
    assertFails { sut.process(inputs, mockRepoFactory()) }
  }

  @Test
  fun should_get_full_input() = runTest {
    // given
    initFullValidEnvironment()
    val inputs = createInputMock(DEFAULT_INPUT_DATA)

    // when
    val result = sut.process(inputs, mockRepoFactory())

    // then
    val expected = ActionInputs(
      owner = "test owner",
      repo = "test repo",
      ref = "test ref",
      workflowName = "test workflow",
      payload = JsonObject(mapOf("testValue" to JsonPrimitive("42"))),
      token = "test token",
      failOnError = true,
      useIdentifierStep = true,
      runId = "test runId",
      triggerWorkflowRunPollConfig = PollingConfig(
        timeout = 7.seconds,
        interval = 13.seconds,
      ),
      waitForRunPollConfig = PollingConfig(
        timeout = 17.minutes,
        interval = 3.hours
      )
    )
    assertEquals(expected, result)
  }

  @Test
  fun should_fallback_to_repo_from_env_if_not_in_input() = runTest {
    // given
    initFullValidEnvironment()
    val inputs = createInputMock(DEFAULT_INPUT_DATA.toMutableMap().also {
      it.remove("repo")
    })

    // when
    val result = sut.process(inputs, mockRepoFactory())

    // then
    assertEquals("testrepo", result.repo)
    assertEquals("test owner", result.owner)
  }

  @Test
  fun should_fallback_to_owner_from_env_if_not_in_input() = runTest {
    // given
    initFullValidEnvironment()
    val inputs = createInputMock(DEFAULT_INPUT_DATA.toMutableMap().also {
      it.remove("owner")
    })

    // when
    val result = sut.process(inputs, mockRepoFactory())

    // then
    assertEquals("test repo", result.repo)
    assertEquals("testowner", result.owner)
  }

  @Test
  fun should_use_defaults() = runTest {
    // given
    initFullValidEnvironment()
    val inputs = createInputMock(DEFAULT_INPUT_DATA.toMutableMap().also {
      it.remove("payload")
      it.remove("fail-on-error")
      it.remove("use-marker-step")
      it.remove("trigger-timeout")
      it.remove("trigger-interval")
      it.remove("wait-timeout")
      it.remove("wait-interval")
    })

    // when
    val result = sut.process(inputs, mockRepoFactory())

    // then
    assertEquals(JsonObject(mapOf()), result.payload, "Payload")
    assertEquals(false, result.failOnError, "FailOnError")
    assertEquals(false, result.useIdentifierStep, "UseMarkerStep")
    assertEquals(1.minutes, result.triggerWorkflowRunPollConfig.timeout, "TriggerTimeout")
    assertEquals(1.seconds, result.triggerWorkflowRunPollConfig.interval, "TriggerInterval")
    assertEquals(10.minutes, result.waitForRunPollConfig.timeout, "WaitTimeout")
    assertEquals(1.seconds, result.waitForRunPollConfig.interval, "WaitInterval")
  }

  @Test
  fun should_retrieve_default_branch_if_no_ref_given() = runTest {
    // given
    val inputs = createInputMock(DEFAULT_INPUT_DATA.toMutableMap().also {
      it.remove("ref")
    })
    everySuspend { repoPortMock.getDefaultBranch(any(), any()) } returns Result.Ok("New default branch")

    // when
    val result = sut.process(inputs, mockRepoFactory())

    // then
    assertEquals("New default branch", result.ref)
  }

  @Test
  fun should_throw_if_default_branch_could_not_retrieved() = runTest {
    // given
    val inputs = createInputMock(DEFAULT_INPUT_DATA.toMutableMap().also {
      it.remove("ref")
    })
    everySuspend { repoPortMock.getDefaultBranch(any(), any()) } returns Result.Error("Error")

    // when
    val ex = assertFails {  sut.process(inputs, mockRepoFactory())}

    // then
    val message = assertIs<ActionFailedException>(ex).message
    assertEquals("Error", message)
  }

  private fun createInputMock(inputs: Map<String, String> = mapOf<String, String>()) = object : Delegatable(true) {
    override fun getOptional(name: String) = inputs[name]
    override fun getRequired(name: String) = inputs[name] ?: throw IllegalStateException("No input for $name")
  }

  private fun mockRepoFactory(): (String) -> RepositoryPort = { repoPortMock }

  private fun initFullValidEnvironment() {
    process.env["GITHUB_REPOSITORY"] = "testowner/testrepo"
    process.env["GITHUB_RUN_ID"] = "42"
    process.env["GITHUB_JOB"] = "testJob"
    process.env["GITHUB_API_URL"] = "http://localhost:9999/api"
    process.env["GITHUB_GRAPHQL_URL"] = "http://localhost:9999/gql"
  }

  companion object {
    private val DEFAULT_INPUT_DATA = mapOf<String, String>(
      "token" to "test token",
      "owner" to "test owner",
      "repo" to "test repo",
      "ref" to "test ref",
      "workflow-name" to "test workflow",
      "payload" to """{ "testValue" : "42" }""",
      "fail-on-error" to "true",
      "use-marker-step" to "true",
      "run-id" to "test runId",
      "trigger-timeout" to "7s",
      "trigger-interval" to "13s",
      "wait-timeout" to "17m",
      "wait-interval" to "3h"
    )
  }
}

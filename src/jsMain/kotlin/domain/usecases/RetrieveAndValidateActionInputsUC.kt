package domain.usecases

import com.rnett.action.core.logger
import com.rnett.action.core.maskSecret
import com.rnett.action.delegates.Delegatable
import domain.model.ActionInputs
import domain.model.PollingConfig
import domain.model.Result
import domain.ports.RepositoryPort
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import utils.actions.ActionEnvironment
import utils.actions.ActionFailedException
import kotlin.time.Duration
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Duration.Companion.seconds

class RetrieveAndValidateActionInputsUC {

  suspend fun process(ghInputs: Delegatable, repositoryFactory: (String) -> RepositoryPort) = logger.withGroup("Processing inputs") {
    var actionInputs = readInputs(ghInputs)

    validateMandatoryInputs(actionInputs)

    if (actionInputs.ref.isNullOrBlank()) {
      detectDefaultBranch(actionInputs, repositoryFactory(actionInputs.token))
    }

    actionInputs
  }

  private fun readInputs(ghInputs: Delegatable): ActionInputs {
    val token = ghInputs.getRequired("token").apply { maskSecret() }
    val (currOwner, currRepo) = ActionEnvironment.GITHUB_REPOSITORY.split('/')
    return ActionInputs(
      ghInputs.orElse("owner") { currOwner },
      ghInputs.orElse("repo") { currRepo },
      ghInputs.getOptional("ref"),
      ghInputs.getOptional("workflow-name"),
      Json.parseToJsonElement(ghInputs.orElse("payload") { "{}" }).jsonObject,
      token,
      ghInputs.getOptional("fail-on-error")?.toBooleanStrictOrNull() == true,
      ghInputs.getOptional("use-marker-step")?.toBooleanStrictOrNull() == true,
      ghInputs.getOptional("run-id"),
      triggerWorkflowRunPollConfig = PollingConfig(
        timeout = ghInputs.getDuration("trigger-timeout", 1.minutes),
        interval = ghInputs.getDuration("trigger-interval", 1.seconds)
      ),
      waitForRunPollConfig = PollingConfig(
        timeout = ghInputs.getDuration("wait-timeout", 10.minutes),
        interval = ghInputs.getDuration("wait-interval", 1.seconds)
      )
    ).also {
      logger.info("Got inputs: $it")
    }
  }

  private fun validateMandatoryInputs(inputs: ActionInputs) {
    if (inputs.token.isBlank()) {
      throw ActionFailedException("Token must not be empty or blank!")
    }

    if ((null == inputs.workflowName) && (null == inputs.runId)) {
      throw ActionFailedException("Either workflow-name or run-id must be set!")
    }
  }

  private suspend fun detectDefaultBranch(inputs: ActionInputs, repo: RepositoryPort) {
    logger.info("No branch given, try to detect default branch")
    val result = repo.getDefaultBranch(inputs.owner, inputs.repo)
    when(result) {
      is Result.Ok -> inputs.ref = result.value
      is Result.Error -> throw ActionFailedException(result.errorMessage)
    }
  }

  private fun Delegatable.getDuration(key: String, default: Duration) = getOptional(key)?.let {
    Duration.parse(it)
  } ?: default

  private fun Delegatable.orElse(key: String, default: () -> String) = getOptional(key) ?: default()
}

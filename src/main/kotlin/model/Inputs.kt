package model

import com.rnett.action.core.inputs
import com.rnett.action.core.logger
import com.rnett.action.core.maskSecret
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonObject
import utils.actions.ActionEnvironment
import kotlin.time.Duration
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Duration.Companion.seconds

data class Inputs(
  val owner: String,
  val repo: String,
  var ref: String?,
  val workflowName: String?,
  var payload: JsonObject,
  val token: String,
  val failOnError: Boolean = false,
  val useIdentifierStep: Boolean,
  var runId: String? = null,
  val triggerTimeout: Duration,
  val triggerInterval: Duration,
  val waitTimeout: Duration,
  val waitInterval: Duration
) {
  companion object {
    fun resolveInputs() = logger.withGroup("Reading inputs") {
      val (currOwner, currRepo) = ActionEnvironment.GITHUB_REPOSITORY.split('/')
      Inputs(
        inputs.getOrElse("owner") { currOwner },
        inputs.getOrElse("repo") { currRepo },
        inputs.getOptional("ref"),
        inputs.getRequired("workflow-name"),
        Json.parseToJsonElement(inputs.getOrElse("payload") { "{}" }).jsonObject,
        inputs.getRequired("token").apply { maskSecret() },
        inputs.getOptional("fail-on-error")?.toBooleanStrictOrNull() ?: false,
        inputs.getOptional("use-marker-step")?.toBooleanStrictOrNull() ?: false,
        inputs.getOptional("run-id"),
        getDuration("trigger-timeout", 1.minutes),
        getDuration("trigger-interval", 1.seconds),
        getDuration("wait-timeout", 10.minutes),
        getDuration("wait-interval", 1.seconds)
      ).also {
        logger.info("Got inputs: $it")
      }
    }

    private fun getDuration(key: String, default: Duration) = inputs.getOptional(key)?.let {
      Duration.parse(it)
    } ?: default
  }
}

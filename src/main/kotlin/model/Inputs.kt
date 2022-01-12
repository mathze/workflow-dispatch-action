package model

import kotlinx.serialization.json.JsonObject

data class Inputs(
  val owner: String,
  val repo: String,
  var ref: String?,
  val workflowName: String,
  var payload: JsonObject,
  val token: String,
  val failOnError: Boolean = false,
  val useIdentifierStep: Boolean,
  var externalRunId: String? = null
)

package model

import kotlinx.serialization.json.JsonObject

data class Inputs(
  val owner: String,
  val repo: String,
  var ref: String?,
  val workflowName: String,
  val payload: JsonObject,
  val token: String,
  val failOnError: Boolean = false
)

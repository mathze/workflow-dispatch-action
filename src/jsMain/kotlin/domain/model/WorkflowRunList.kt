package domain.model

import kotlin.js.Date

data class WorkflowRunList(
  val workflowId: String,
  val ref: String,
  val dispatchTime: Date,
  val runs:List<WorkflowRun> = listOf(),
  val eTag: String? = null)

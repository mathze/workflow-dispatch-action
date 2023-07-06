package utils

import com.rnett.action.core.fail
import com.rnett.action.core.logger.error
import com.rnett.action.core.outputs
import com.rnett.action.currentProcess
import web.timers.setTimeout
import kotlin.coroutines.resume
import kotlin.coroutines.suspendCoroutine
import kotlin.time.Duration

/**
 * Controls how an error in the action is carried out to the workflow.
 *
 * Always sets the outputs 'failed' variable to "true"!
 *
 * @param[message] The message to display in the log.
 * @param[failOnError] If `true` action terminates with exit code `1` which will also marks the workflow run as failed.
 *  If `false` action terminates with exit code `0` which marks the step "passed" and workflow continues normally.
 */
fun failOrError(message: String, failOnError: Boolean) {
  // if we report any failure, we consider the action as failed, but maybe don't want the workflow to fail
  outputs["failed"] = "true"
  if (failOnError) {
    fail(message)
  } else {
    error(message)
    currentProcess.exit(0)
  }
}

suspend fun delay(duration: Duration): Unit = suspendCoroutine { continuation ->
  setTimeout(duration) { continuation.resume(Unit) }
}

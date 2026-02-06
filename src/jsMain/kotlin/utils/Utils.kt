package utils

import com.rnett.action.core.fail
import com.rnett.action.core.logger
import com.rnett.action.core.logger.error
import com.rnett.action.core.outputs
import com.rnett.action.currentProcess
import web.timers.setTimeout
import kotlin.coroutines.resume
import kotlin.coroutines.suspendCoroutine
import kotlin.js.Date
import kotlin.math.abs
import kotlin.time.Duration
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.DurationUnit
import kotlin.time.toDuration

/**
 * Controls how an error in the action is carried out to the workflow.
 *
 * Always sets the outputs 'failed' variable to "true"!
 *
 * @param[exception] The exception to display in the log.
 * @param[failOnError] If `true` action terminates with exit code `1` which will also marks the workflow run as failed.
 *  If `false` action terminates with exit code `0` which marks the step "passed" and workflow continues normally.
 */
fun failOrError(exception: Throwable, failOnError: Boolean) {
  // if we report any failure, we consider the action as failed, but maybe don't want the workflow to fail
  outputs["failed"] = "true"
  if (failOnError) {
    fail(exception)
  } else {
    error(exception)
    currentProcess.exit(0)
  }
}

/**
 * Support function to execute [block] in a loop.
 *
 * The times block will be invoked depends on the [maxTimeout], the [frequency]
 * and the execution time of [block] itself.
 *
 * To identify if polling can be stopped, [block] has to return a pair which first member
 * is `true`, otherwise function waits [frequency] and executes [block].
 *
 * @param[maxTimeout] The maximum duration of total execute time before returning to caller.
 * @param[frequency] The duration to wait between two consecutive [block] invocations.
 * @param[block] The function to execute in loop.
 *
 * @return The result of [block] invocation.
 */
internal suspend inline fun <T> executePolling(
  maxTimeout: Duration, frequency: Duration, block: () -> Pair<Boolean, T>
): Pair<Boolean, T> {
  val start = getTimeMillis()
  var delta: Duration
  var result: Pair<Boolean, T>
  do {
    result = block().also {
      if (!it.first) {
        logger.info("No result, retry in $frequency")
        delay(frequency)
      }
    }
    delta = getTimeMillis().deltaMs(start)
    logger.debug("Time passed since start: $delta")
  } while (!result.first && (delta < maxTimeout))

  return result
}

/**
 * Delays further execution for the specified duration.
 *
 * @param[duration] Duration to wait until continue.
 */
suspend fun delay(duration: Duration): Unit = suspendCoroutine { continuation ->
  setTimeout(duration) { continuation.resume(Unit) }
}

/**
 * Extension to retrieve the duration between two integer timestamps.
 *
 * Note: `start.deltaMs(end) == end.deltaMs(start)`, meaning order does not matter.
 *
 * Example:
 * ```
 * val start = getTimeMillis()
 * // Do some work ...
 * val timePassed = getTimeMillis().deltaMs(start)
 * println("Processing took $timePassed")
 * ```
 *
 * @param[other] The second timestamp in milliseconds.
 *
 * @return The time passed between this and [other] as [Duration].
 */
internal fun Long.deltaMs(other: Long): Duration {
  val delta = abs(this - other)
  return delta.toDuration(DurationUnit.MILLISECONDS)
}

/**
 * Retries a given [block] once if it fails with an error.
 *
 * @param[wait] The time to wait before calling [block] a second time.
 * Only applies if first call fails.
 * @param[block] The block that shall be executed. Might get executed twice in case first call had thrown an error.
 *
 * @return The result of [block]
 *
 * @throws[Throwable] Everything that can be thrown by [block].
 */
suspend inline fun <T> retryOnce(wait: Duration = 100.milliseconds, block: suspend () -> T): T = try {
  block()
} catch (_: Throwable) {
  logger.debug("Caught exception... retrying")
  delay(wait)
  block()
}

fun getTimeMillis() = Date().getTime().toLong()

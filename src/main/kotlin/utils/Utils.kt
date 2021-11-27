package utils

import com.rnett.action.core.logger.error
import com.rnett.action.core.logger.fatal
import com.rnett.action.core.outputs
import kotlin.contracts.ExperimentalContracts
import kotlin.contracts.InvocationKind
import kotlin.contracts.contract

@OptIn(ExperimentalContracts::class)
inline fun <T> runAction(
  before: () -> T,
  catch: (input: T, ex: Throwable) -> Unit,
  block: (input: T) -> Unit
) {
  contract { callsInPlace(block, InvocationKind.EXACTLY_ONCE) }
  outputs["failed"] = "true"
  val a = before()
  try {
    block(a)
    outputs["failed"] = "false"
  } catch (e: Throwable) {
    catch(a, e)
  }
}

fun failOrError(
  message: String,
  failOnError: Boolean
) {
  // if we report any failure, consider the action to have failed, may not make the build fail
  outputs["failed"] = "true"
  if (failOnError) {
    fatal(message)
  } else {
    error(message)
  }
}
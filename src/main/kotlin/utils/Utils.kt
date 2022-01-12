package utils

import utils.actions.setFailed
import utils.actions.setOutput
import kotlin.contracts.ExperimentalContracts
import kotlin.contracts.InvocationKind
import kotlin.contracts.contract

@OptIn(ExperimentalContracts::class)
inline fun <T> runAction(
  before: () -> T,
  catch: (input:T, ex:Throwable) -> Unit,
  block: (input: T) -> Unit
) {
  contract { callsInPlace(block, InvocationKind.EXACTLY_ONCE) }
  setOutput("failed", true)
  val a = before()
  try {
    block(a)
    setOutput("failed", false)
  } catch (e: Throwable) {
    catch(a, e)
  }
}

fun failOrError(
  message: String,
  failOnError: Boolean
) {
  // if we report any failure, consider the action to have failed, may not make the build fail
  setOutput("failed", true)
  if (failOnError) {
    setFailed(message)
  } else {
    error(message)
  }
}
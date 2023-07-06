package utils.actions

/**
 * Exception to indicate a controlled action exit.
 */
class ActionFailedException(message: String?, cause: Throwable?): Throwable(message, cause) {
  constructor(message: String?): this(message, null)
}
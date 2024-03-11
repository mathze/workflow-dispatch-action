package utils.actions

/**
 * Exception to indicate a controlled action exit.
 */
class ActionFailedException(message: String?, cause: Throwable? = null): Throwable(message, cause)

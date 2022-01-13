package utils.actions

class ActionFailedException(override val message: String?, override val cause: Throwable?): Throwable(message, cause) {
  constructor(message: String?): this(message, null)
}
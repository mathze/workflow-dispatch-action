package domain.model

sealed class Result<out T> {
  data class Error(val errorMessage: String) : Result<Nothing>()
  data class Ok<out T>(val value: T) : Result<T>()

  companion object {
    fun <T> ok(value: T) = Ok(value)
    fun error(message: String) = Error(message)
  }
}

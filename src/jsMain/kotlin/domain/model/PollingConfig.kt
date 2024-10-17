package domain.model

import kotlin.time.Duration

data class PollingConfig(
  /**
   * Overall duration to wait.
   */
  val timeout: Duration,
  /**
   * Delay between consecutive operations.
   */
  val interval: Duration
)

package utils

import dev.mokkery.answering.returns
import dev.mokkery.answering.sequentially
import dev.mokkery.answering.throwsErrorWith
import dev.mokkery.every
import dev.mokkery.matcher.any
import dev.mokkery.mock
import dev.mokkery.verify
import dev.mokkery.verify.VerifyMode.Companion.exactly
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFails
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.measureTime
import kotlin.time.measureTimedValue

class UtilsUnitTest {

  @Test
  fun shouldDelay() = runTest {
    // given
    val toWait = 250.milliseconds

    // when
    val elapsed = measureTime { delay(toWait) }

    // then
    assertTrue(elapsed > toWait)
  }

  @Test
  fun should_calculate_deltaMs_start_before_end() {
    // given
    val start = 1000L
    val end = 2500L

    // when
    val delta = start.deltaMs(end)

    // then
    assertEquals(1500.milliseconds, delta)
  }

  @Test
  fun should_calculate_deltaMs_start_after_end() {
    // given
    val start = 2500L
    val end = 1500L

    // when
    val delta = start.deltaMs(end)

    // then
    assertEquals(1000.milliseconds, delta)
  }

  @Test
  fun should_retryOnce_successful_with_default_delay() = runTest {
    // given
    val mock = mock<List<String>>()
    every { mock[any()] } sequentially {
      throwsErrorWith("First")
      returns("Hello")
    }

    // when
    val res = measureTimedValue {
      retryOnce { mock[5] }
    }

    // then
    assertEquals("Hello", res.value)
    verify(exactly(2)) { mock[any()] }
    assertTrue(100.milliseconds <= res.duration, "Delay lower bound")
    assertTrue(120.milliseconds > res.duration, "Delay upper bound")
  }

  @Test
  fun should_retryOnce_successful_with_custom_delay() = runTest {
    // given
    val mock = mock<List<String>>()
    every { mock[any()] } sequentially {
      throwsErrorWith("First")
      returns("Hello")
    }

    // when
    val res = measureTimedValue {
      retryOnce(10.milliseconds) { mock[5] }
    }

    // then
    assertEquals("Hello", res.value)
    verify(exactly(2)) { mock[any()] }
    assertTrue(10.milliseconds <= res.duration, "Delay lower bound")
    assertTrue(20.milliseconds > res.duration, "Delay upper bound")
  }

  @Test
  fun should_retryOnce_on_first_call_successful_no_delay() = runTest {
    // given
    val mock = mock<List<String>>()
    every { mock[any()] } returns "Hello"

    // when
    val res = measureTimedValue {
      retryOnce(10.milliseconds) { mock[5] }
    }

    // then
    assertEquals("Hello", res.value)
    verify(exactly(1)) { mock[any()] }
  }

  @Test
  fun should_retryOnce_failing() = runTest {
    // given
    val mock = mock<List<String>>()
    every { mock[any()] } throwsErrorWith ("Error")

    // when
    val res = assertFails {
      retryOnce(10.milliseconds) { mock[5] }
    }

    // then
    assertEquals("Error", res.message)
    verify(exactly(2)) { mock[any()] }
  }
}

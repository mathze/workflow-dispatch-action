import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runTest

@JsModule("./mockServer.mjs")
@JsNonModule
external class MockServer {
  fun startMockServer()

  fun stopMockServer()
}

fun withMockServer(test: suspend TestScope.() -> Unit) = runTest {
  val mockServer = MockServer()
  mockServer.startMockServer()
  try {
    test()
  } finally {
    mockServer.stopMockServer()
  }
}

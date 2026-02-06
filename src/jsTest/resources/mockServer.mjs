// mockServer.js
import { setupServer } from 'msw/node';
import { http, HttpResponse } from 'msw'
import { handlers } from './handlers.mjs'

export default class MockServer {
  server = setupServer(
    ...handlers,
    // last an any block
    http.all(
      /.*/,
      () => new HttpResponse(
        null,
        {
          status: 500,
          statusText: "Error!"
        }
      )
    )
  );

  startMockServer() {
    this.server.listen();
    console.debug("Mock server started");
  }

  stopMockServer() {
    this.server.close()
    console.debug("Mock server stopped");
  }
}

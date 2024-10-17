import { http, HttpResponse } from 'msw'

const urlBase = 'http://localhost:8080/repos/owner/repo/actions/workflows'
export const handler = [
  // should_retrieve_id_from_workflow_name
  http.get(
    `${urlBase}/test.yml`,
    () => {
      return HttpResponse.json(
        {
          id: 42,
        }
      );
    }
  ),
  // retrieve_workflow_id_should_handle_error_gracefully
  http.get(
    `${urlBase}/asd`,
    () => {
      return new HttpResponse(
        null,
        {
          status: 500,
          statusText: 'Invalid request!'
        }
      );
    }
  ),
  // retrieve_workflow_id_should_fail_on_invalid_json
  http.get(
    `${urlBase}/invalid.yml`,
    () => {
      return HttpResponse.json(
        {
          identifier: 123,
        }
      );
    }
  ),
  // retrieve_workflow_id_should_retry_on_network_issue
  http.get(
    `${urlBase}/network_error.yml`,
    function* () {
      let firstCall = true;

      if (firstCall) {
        firstCall = false;
        console.info('Respond with error');
        yield HttpResponse.error();
      }

      console.info('Respond with ok');
      return HttpResponse.json(
        {
          id: 42,
        }
      );
    }
  ),

  // should_trigger_workflow_and_use_date_from_response
  http.post(
    `${urlBase}/11/dispatches`,
    async ({request}) => {
      const reqBody = await request.json();
      const ref = reqBody.ref;
      const inputs = reqBody.inputs;
      const greeting = inputs.greeting;
      if (('main' === ref) && ('Hello world' === greeting)) {
        return new HttpResponse(
          null,
          {
            status: 200,
            headers: {
              date: '2029-11-05 13:07:42Z',
            },
          }
        );
      } else {
        return new HttpResponse(
          null,
          {
            status: 500,
            statusText: 'Invalid request!'
          }
        );
      }
    }
  ),
  // should_trigger_workflow_and_use_fallback_date
  http.post(
    `${urlBase}/13/dispatches`,
    async ({request}) => {
      const reqBody = await request.json();
      const ref = reqBody.ref;
      if ('no_date' === ref) {
        return new HttpResponse(
          null,
          {
            status: 200,
          }
        );
      } else {
        return new HttpResponse(
          null,
          {
            status: 500,
            statusText: 'Invalid request!'
          }
        );
      }
    }
  ),
  // trigger_workflow_should_handle_error_gracefully
  http.post(
    `${urlBase}/7/dispatches`,
    async () => {
      return new HttpResponse(
        null,
        {
          status: 500,
          statusText: 'Error!'
        }
      );
    }
  ),
  // trigger_workflow_should_retry_on_network_issue
  http.post(
    `${urlBase}/23/dispatches`,
    async function* () {
      let firstCall = true;

      if (firstCall) {
        firstCall = false;
        console.info('Respond with error');
        yield HttpResponse.error();
      }

      console.info('Respond with ok');
      return new HttpResponse(
        null,
        {
          status: 200,
          headers: {
            date: '2023-10-05 19:47:42Z',
          },
        }
      );
    }
  ),
];

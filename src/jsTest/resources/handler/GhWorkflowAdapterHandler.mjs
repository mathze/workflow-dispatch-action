import { http, HttpResponse } from 'msw'

const urlBase = 'http://localhost:8080/repos/owner/repo/actions/workflows'
export const handler = [
  // GhWorkflowAdapterIntegrationTest#should_retrieve_id_from_workflow_name
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
  // GhWorkflowAdapterIntegrationTest#retrieve_workflow_id_should_handle_error_gracefully
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

  // GhWorkflowAdapterIntegrationTest#retrieve_workflow_id_should_fail_on_invalid_json
  http.get(
    `${urlBase}/invalid.yml`,
    () => {
      return HttpResponse.json(
        {
          identifier: 123,
          name: "Invalid",
        }
      );
    }
  ),
  // GhWorkflowAdapterIntegrationTest#should_trigger_workflow_and_use_date_from_response
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
  // GhWorkflowAdapterIntegrationTest#should_trigger_workflow_and_use_fallback_date
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
  // GhWorkflowAdapterIntegrationTest#trigger_workflow_should_handle_error_gracefully
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
];

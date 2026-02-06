import { http, HttpResponse } from 'msw'

export const handler = [
  http.get(
    'http://localhost:8080/repos/owner/repo/actions/runs',
    ({request, response, }) => {

      const url = new URL(request.url);
      const query = url.searchParams;
      const ref = query.get('branch');
      return mockResponses.get(ref);
    }
  ),
  // should_retry_on_network_issue
  http.get(
    'http://localhost:9090/repos/owner/repo/actions/runs',
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
          total_count: 1,
          workflow_runs: [
            {
              id: 99,
              workflow_id: 64,
            },
          ],
        },
        {
          headers: {
            etag: "eTag 4 network issue"
          }
        }
      );
    }
  ),
];

const mockResponses = new Map([
  [ // should_retrieve_workflow_runs_no_eTag
    'ok no eTag',
    HttpResponse.json(
      {
        total_count: 1,
        workflow_runs: [
          {
            id: 13,
            workflow_id: 42,
          },
        ],
      }
    )
  ],
  [ // should_retrieve_workflow_runs_with_new_eTag
    'ok new eTag',
    HttpResponse.json(
      {
        total_count: 1,
        workflow_runs: [
          {
            id: 13,
            workflow_id: 42,
          },
        ],
      },
      {
        headers: {
          etag: "new eTag"
        }
      }
    )
  ],
  [ // should_only_retrieve_jobs_if_workflow_id_matches
    'multiple workflow runs',
    HttpResponse.json(
      {
        total_count: 1,
        workflow_runs: [
          {
            id: 13,
            workflow_id: 42,
          },
          {
            id: 19,
            workflow_id: 42,
          },
          {
            id: 23,
            workflow_id: 21,
          },
        ],
      },
      {
        headers: {
          etag: "new eTag"
        }
      }
    )
  ],
  [ // should_retrieve_workflow_runs_with_new_eTag
    'ok new eTag',
    HttpResponse.json(
      {
        total_count: 1,
        workflow_runs: [
          {
            id: 13,
            workflow_id: 42,
          },
        ],
      },
      {
        headers: {
          etag: "new eTag"
        }
      }
    )
  ],
  [ // should_keep_existing_workflow_runs_if_not_modified
    'not modified',
     new HttpResponse(null, { status: 304 })
  ],
  [ // should_throw_on_response_parsing_issues_no_workflow_id
    'parsing issue no workflow_id',
    HttpResponse.json(
      {
        total_count: 0,
        workflow_runs: [
          {
            id: 17,
            workflowid: 15,
          }
        ]
      }
    )
  ],
  [ // should_throw_on_response_parsing_issues_no_workflow_runs
    'parsing issue no workflow_runs',
    HttpResponse.json(
      {
        total_count: 0,
        workflowruns: [
          {
            id: 17,
            workflow_id: 15,
          }
        ]
      }
    )
  ],
  [ // should_throw_on_response_parsing_issues_no_workflow_runs
    'parsing issue no id',
    HttpResponse.json(
      {
        total_count: 0,
        workflow_runs: [
          {
            identifier: 17,
            workflow_id: 42,
          }
        ]
      }
    )
  ],
]);

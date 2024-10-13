import { http, delay, HttpResponse } from 'msw'

const urlBase = 'http://localhost:8080/repos/owner/repo/jobs'
export const handler = [
  // GhJobAdapterIntegrationTest#should_fetch_and_map_jobList
  http.get(
    `${urlBase}/42`,
    () => {
      return HttpResponse.json(
        {
          jobs: [
            {
              steps: [
                { name: 'dummy' },
              ],
            },
          ],
        },
        {
          headers: {
            etag: 'new eTag',
          }
        }
     );
    }
  ),
  // GhJobAdapterIntegrationTest#should_handle_not_modified_and_return_initial_jobList
  http.get(
    `${urlBase}/13`,
    ({request}) => {
      let etag = request.headers.get("If-None-Match");
      if ('Tag13' === etag) {
        return new HttpResponse(
          null,
          {
            status: 304,
            statusText: "Not Modified",
            headers: {
              etag: "new eTag"
            }
          }
        );
      } else {
        return new HttpResponse(
          "Invalid etag!",
          {
            status: 401,
            statusText: "Invalid",
          }
        );
      }
    }
  ),
  // GhJobAdapterIntegrationTest#should_handle_errors
  http.get(
    `${urlBase}/error`,
    () => {
      return new HttpResponse(
        null,
        {
          status: 401,
          statusText: 'Error'
        }
      );
    }
  ),
];

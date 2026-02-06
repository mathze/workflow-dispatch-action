import { http, delay, HttpResponse } from 'msw'

const urlBase = 'http://localhost:8080/repos/owner/repo/jobs'
export const handler = [
  // should_fetch_and_map_jobList
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
  // should_handle_not_modified_and_return_initial_jobList
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
  // should_retry_on_error
  http.get(
    `${urlBase}/error`,
    function* () {
      let firstRun = true;
      if (firstRun) {
        firstRun = false;
        console.info('Respond with error');
        yield HttpResponse.error();
      }

      console.info('Respond with ok');
      return HttpResponse.json(
         {
           jobs: [
             {
               steps: [
                 { name: 'after network error' },
               ],
             },
           ],
         }
      );
    }
  ),
];

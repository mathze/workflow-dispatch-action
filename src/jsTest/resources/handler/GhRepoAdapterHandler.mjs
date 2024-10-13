import { graphql, delay, HttpResponse } from 'msw'

const gql = graphql.link('http://localhost:8080/gql');
export const handler = [
  gql.query(
    'GetDefaultBranch',
    ({ query, variables }) => {
      const { name } = variables;
      return responses.get(name);
    }
  ),
];

const responses = new Map([
  [ // should_get_default_branch_name
    'ok',
    HttpResponse.json(
      {
        data: {
          repository: {
            defaultBranchRef:{
              name: 'default branch'
            }
          }
        }
      }
    )
  ],
  [ // should_handle_parsing_issues_gracefully_no_name
    'resp not ok - no name',
    HttpResponse.json(
      {
        data: {
          repository: {
            defaultBranchRef: {
              nom: 'default branch'
            }
          }
        }
      }
    )
  ],
  [ // should_handle_parsing_issues_gracefully_no_defaultBranchRef
    'resp not ok - no defaultBranchRef',
    HttpResponse.json(
      {
        data: {
          repository: {
            defaultBrunchRef: {
              name: 'default branch'
            }
          }
        }
      }
    )
  ],
  [ // should_handle_parsing_issues_gracefully_no_repository
    'resp not ok - no repository',
    HttpResponse.json(
      {
        data: {
          repo: {
            defaultBranchRef: {
              name: 'default branch'
            }
          }
        }
      }
    )
  ],
  [ // should_handle_parsing_issues_gracefully_no_data
    'resp not ok - no data',
    HttpResponse.json(
      {
        date: {
          repository: {
            defaultBranchRef: {
              name: 'default branch'
            }
          }
        }
      }
    )
  ],
  [ // should_handle_response_issues_gracefully
    'resp not ok - error',
    new HttpResponse(
      null,
      {
        status: 401,
        statusText: 'Error'
      }
    )
  ],
]);

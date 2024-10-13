import { handler as jobHandler } from './handler/GhJobAdapterHandler.mjs'
import { handler as workflowHandler } from './handler/GhWorkflowAdapterHandler.mjs'
import { handler as repoHandler } from './handler/GhRepoAdapterHandler.mjs'
import { handler as runHandler } from './handler/GhWorkflowRunAdapterHandler.mjs'

export const handlers = [
  ...jobHandler,
  ...workflowHandler,
  ...repoHandler,
  ...runHandler
]

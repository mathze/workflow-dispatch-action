package utils.actions

import NodeJS.get
import process
import kotlin.reflect.KProperty

/**
 * Subset of Environment variables used by this action.
 */
object ActionEnvironment {
  /**
   * The owner and repository of the workflow the action is executed in.
   */
  val GITHUB_REPOSITORY by Environment

  /**
   * The id of the current workflow run.
   * Same over multiple re-runs.
   */
  val GITHUB_RUN_ID by Environment

  /**
   * The job_id of the current job.
   */
  val GITHUB_JOB by Environment

  /**
   * URL to GitHub's rest api.
   */
  val GITHUB_API_URL by Environment

  /**
   * URL to GitHub's GraphQL api.
   */
  val GITHUB_GRAPHQL_URL by Environment

  private object Environment {
    operator fun getValue(env: Any, property: KProperty<*>): String =
      process.env[property.name] ?: throw ActionFailedException("Could not find ${property.name} in process.env!")
  }
}
package utils.actions

import NodeJS.get
import process
import kotlin.reflect.KProperty

object ActionEnvironment {
  val GITHUB_REPOSITORY by Environment

  /**
   * The id of the current workflow run.
   * Same over multiple re-runs.
   */
  val GITHUB_RUN_ID by Environment

  /**
   * The number of (re-)runs of a certain workflow-run.
   */
  val GITHUB_RUN_NUMBER by Environment

  /**
   * The user that triggers the workflow run.
   */
  val GITHUB_ACTOR by Environment

  /**
   * URL to GitHub's rest api.
   */
  val GITHUB_API_URL by Environment

  /**
   * URL to GitHub's GraphQL api.
   */
  val GITHUB_GRAPHQL_URL by Environment
}

private object Environment {
  operator fun getValue(env: Any, property: KProperty<*>): String = 
    process.env[property.name] ?: throw ActionFailedException("Could not find ${property.name} in process.env!")
}
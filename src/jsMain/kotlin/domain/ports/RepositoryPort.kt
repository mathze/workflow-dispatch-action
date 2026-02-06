package domain.ports

import domain.model.Result

interface RepositoryPort {
  suspend fun getDefaultBranch(owner: String, repository: String): Result<String>
}

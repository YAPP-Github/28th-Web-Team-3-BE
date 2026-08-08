package backend.yapp.core.policy.domain

import org.springframework.data.domain.Page
import org.springframework.data.domain.Pageable
import org.springframework.data.jpa.repository.JpaRepository

interface YouthPolicyRepository : JpaRepository<YouthPolicy, Long> {
    fun findByExternalId(externalId: String): YouthPolicy?

    fun findByCategory(category: String, pageable: Pageable): Page<YouthPolicy>
}

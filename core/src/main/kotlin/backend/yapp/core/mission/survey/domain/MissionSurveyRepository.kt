package backend.yapp.core.mission.survey.domain

import org.springframework.data.jpa.repository.EntityGraph
import org.springframework.data.jpa.repository.JpaRepository

interface MissionSurveyRepository : JpaRepository<MissionSurvey, Long> {
    @EntityGraph(attributePaths = ["answers"])
    fun findByGuestUserId(guestUserId: Long): MissionSurvey?

    fun deleteByGuestUserId(guestUserId: Long): Long
}

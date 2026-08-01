package backend.yapp.core.mission.survey.domain

import jakarta.persistence.CascadeType
import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.GeneratedValue
import jakarta.persistence.GenerationType
import jakarta.persistence.Id
import jakarta.persistence.OneToMany
import jakarta.persistence.Table
import jakarta.persistence.Version
import java.time.Instant

@Entity
@Table(name = "mission_survey")
class MissionSurvey(
    @Column(name = "guest_user_id", nullable = false, unique = true)
    val guestUserId: Long,
    @Column(name = "schema_version", nullable = false, length = 20)
    val schemaVersion: String = MissionSurveySnapshot.CURRENT_SCHEMA_VERSION,
    @Column(name = "created_at", nullable = false)
    val createdAt: Instant = Instant.now(),
    @Column(name = "updated_at", nullable = false)
    var updatedAt: Instant = Instant.now(),
    @Version
    @Column(name = "version", nullable = false)
    var version: Long = 0,
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    val id: Long = 0,
) {
    @OneToMany(mappedBy = "missionSurvey", cascade = [CascadeType.ALL], orphanRemoval = true)
    private val answers: MutableList<MissionSurveyAnswer> = mutableListOf()

    fun answerRows(): List<MissionSurveyAnswer> = answers.toList()

    fun clearAnswers(now: Instant) {
        answers.clear()
        updatedAt = now
    }

    fun addAnswers(newAnswers: List<MissionSurveyAnswerValue>, now: Instant) {
        newAnswers.forEach { value ->
            answers += MissionSurveyAnswer(
                missionSurvey = this,
                categoryCode = value.categoryCode,
                questionCode = value.questionCode,
                valueType = value.valueType,
                answerCode = value.answerCode,
                numericValue = value.numericValue,
                rangeCode = value.rangeCode,
                textValue = value.textValue,
                unitCode = value.unitCode,
            )
        }
        updatedAt = now
    }
}

data class MissionSurveyAnswerValue(
    val categoryCode: String,
    val questionCode: String,
    val valueType: String,
    val answerCode: String,
    val numericValue: Int? = null,
    val rangeCode: String? = null,
    val textValue: String? = null,
    val unitCode: String? = null,
)

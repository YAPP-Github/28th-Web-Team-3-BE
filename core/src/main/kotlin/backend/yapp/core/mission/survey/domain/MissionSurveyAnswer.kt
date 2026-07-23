package backend.yapp.core.mission.survey.domain

import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.FetchType
import jakarta.persistence.GeneratedValue
import jakarta.persistence.GenerationType
import jakarta.persistence.Id
import jakarta.persistence.JoinColumn
import jakarta.persistence.ManyToOne
import jakarta.persistence.Table

@Entity
@Table(name = "mission_survey_answer")
class MissionSurveyAnswer(
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "mission_survey_id", nullable = false)
    val missionSurvey: MissionSurvey,
    @Column(name = "category_code", nullable = false, length = 20)
    val categoryCode: String,
    @Column(name = "question_code", nullable = false, length = 50)
    val questionCode: String,
    @Column(name = "value_type", nullable = false, length = 10)
    val valueType: String,
    @Column(name = "answer_code", nullable = false, length = 60)
    val answerCode: String,
    @Column(name = "numeric_value")
    val numericValue: Int? = null,
    @Column(name = "unit_code", length = 30)
    val unitCode: String? = null,
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    val id: Long = 0,
)

package backend.yapp.api.mission.survey.controller

import backend.yapp.api.mission.survey.dto.MissionSurveyPutRequest
import backend.yapp.api.mission.survey.dto.MissionSurveyQuestionsResponse
import backend.yapp.api.mission.survey.dto.MissionSurveyResponse
import backend.yapp.apidoc.mission.survey.MissionSurveyApi
import backend.yapp.core.mission.survey.service.MissionSurveyService
import jakarta.validation.Valid
import org.springframework.security.core.annotation.AuthenticationPrincipal
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PutMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty

@RestController
@ConditionalOnProperty(prefix = "mission.survey", name = ["enabled"], havingValue = "true")
@RequestMapping("/api/missions/surveys")
class MissionSurveyController(
    private val missionSurveyService: MissionSurveyService,
) : MissionSurveyApi {
    @GetMapping("/questions")
    override fun questions(
        @AuthenticationPrincipal guestUserId: Long,
        @RequestParam(required = false) categories: List<String>?,
    ): MissionSurveyQuestionsResponse =
        MissionSurveyQuestionsResponse.from(missionSurveyService.questions(categories.orEmpty()))

    @GetMapping
    override fun get(@AuthenticationPrincipal guestUserId: Long): MissionSurveyResponse =
        MissionSurveyResponse.from(missionSurveyService.get(guestUserId))

    @PutMapping
    override fun replace(
        @AuthenticationPrincipal guestUserId: Long,
        @Valid @RequestBody request: MissionSurveyPutRequest,
    ): MissionSurveyResponse =
        MissionSurveyResponse.from(missionSurveyService.replace(guestUserId, request.toCommand()))
}

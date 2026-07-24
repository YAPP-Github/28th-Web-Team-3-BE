package backend.yapp.api.mission.survey

import backend.yapp.core.mission.survey.domain.MissionSurveyQuestionCatalog
import backend.yapp.core.mission.survey.domain.MissionSurveyQuestionCode
import com.jayway.jsonpath.Configuration
import com.jayway.jsonpath.JsonPath
import com.nimbusds.jwt.SignedJWT
import java.util.UUID
import javax.sql.DataSource
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc
import org.springframework.http.MediaType
import org.springframework.test.context.ActiveProfiles
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.ResultActions
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class MissionSurveyAcceptanceTest(
    @Autowired private val mockMvc: MockMvc,
    @Autowired private val dataSource: DataSource,
) {
    @Test
    fun `mission survey endpoints require authentication`() {
        mockMvc.perform(get("$SURVEY_PATH/questions").param("categories", "MEAL"))
            .andExpect(status().isUnauthorized)
        mockMvc.perform(get(SURVEY_PATH))
            .andExpect(status().isUnauthorized)
        mockMvc.perform(
            put(SURVEY_PATH)
                .contentType(MediaType.APPLICATION_JSON)
                .content(MEAL_REQUEST),
        ).andExpect(status().isUnauthorized)
    }

    @Test
    fun `questions are filtered and canonically ordered with branch metadata`() {
        val token = issueGuestToken()

        mockMvc.perform(
            get("$SURVEY_PATH/questions")
                .header("Authorization", "Bearer $token")
                .param("categories", "LIVING", "MEAL"),
        ).andExpect(status().isOk)
            .andExpect(jsonPath("$.categories.length()").value(2))
            .andExpect(jsonPath("$.categories[0].category").value("MEAL"))
            .andExpect(jsonPath("$.categories[1].category").value("LIVING"))
            .andExpect(jsonPath("$.categories[0].questions.length()").value(5))
            .andExpect(jsonPath("$.categories[1].questions.length()").value(5))
            .andExpect(jsonPath("$.categories[0].questions[0].code").value("MEAL_TARGET"))
            .andExpect(jsonPath("$.categories[0].questions[1].code").value("MEAL_FREQUENCY"))
            .andExpect(
                jsonPath("$.categories[0].questions[1].dependsOnQuestionCode")
                    .value("MEAL_TARGET"),
            )
            .andExpect(jsonPath("$.categories[0].questions[1].skipWhenOptionCodes[0]").value("UNKNOWN"))
            .andExpect(
                jsonPath("$.categories[0].questions[1].numericRules[0].subjectOptionCode")
                    .value("DELIVERY"),
            )
            .andExpect(
                jsonPath("$.categories[0].questions[1].numericRules[0].unit")
                    .value("TIMES_PER_WEEK"),
            )
            .andExpect(jsonPath("$.categories[0].questions[1].numericRules[0].minimum").value(0))
            .andExpect(jsonPath("$.categories[0].questions[1].numericRules[0].maximum").value(7))
            .andExpect(
                jsonPath("$.categories[0].questions[1].numericRules[2].subjectOptionCode")
                    .value("PAID_BEVERAGE"),
            )
            .andExpect(jsonPath("$.categories[0].questions[1].numericRules[2].maximum").value(14))
            .andExpect(
                jsonPath("$.categories[0].questions[2].exclusiveOptionCodes[0]")
                    .value("NO_ALTERNATIVE"),
            )
            .andExpect(
                jsonPath("$.categories[1].questions[2].numericRules[0].unit")
                    .value("SUBSCRIPTION_COUNT"),
            )
            .andExpect(
                jsonPath("$.categories[1].questions[2].numericRules[1].unit")
                    .value("TIMES_PER_FOUR_WEEKS"),
            )

        mockMvc.perform(
            get("$SURVEY_PATH/questions")
                .header("Authorization", "Bearer $token")
                .param("categories", "HOBBY"),
        ).andExpect(status().isOk)
            .andExpect(
                jsonPath(
                    "$.categories[0].questions[4].conditionalOptionRules[0].dependsOnQuestionCode",
                ).value("HOBBY_SPENDING_TYPES"),
            )
            .andExpect(
                jsonPath(
                    "$.categories[0].questions[4].conditionalOptionRules[0].whenOptionCodes[0]",
                ).value("DO_NOT_REDUCE"),
            )
            .andExpect(
                jsonPath(
                    "$.categories[0].questions[4].conditionalOptionRules[0].allowedOptionCodes[0]",
                ).value("NO_HOBBY_MISSION"),
            )

        assertEquals(
            listOf("1", "2", "3", "4", "5", "6", "7"),
            queryStrings(
                """
                    SELECT version
                    FROM flyway_schema_history
                    WHERE success = TRUE AND version IS NOT NULL
                    ORDER BY installed_rank
                """.trimIndent(),
            ),
        )
    }

    @Test
    fun `PUT followed by GET returns the same canonical survey`() {
        val token = issueGuestToken()
        val request = """
            {
              "meal": {
                "target": "DELIVERY",
                "weeklyFrequency": 0,
                "alternatives": ["PICKUP", "COOK"],
                "reason": "TIME_OR_ENERGY",
                "exclusions": ["NONE"]
              },
              "transport": null,
              "hobby": {
                "hobbies": ["GAME", "READING"],
                "spendingTypes": ["SUBSCRIPTION", "GOODS"],
                "monthlySpendingRange": "FROM_50K_TO_150K",
                "frequencies": [
                  {"spendingType": "SUBSCRIPTION", "count": 20},
                  {"spendingType": "GOODS", "count": 31}
                ],
                "savingMethods": ["REVIEW_SUBSCRIPTIONS", "WAIT_BEFORE_BUYING"]
              },
              "living": null
            }
        """.trimIndent()

        val putJson = putSurvey(token, request)
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.schemaVersion").value("V1"))
            .andExpect(jsonPath("$.meal.weeklyFrequency").value(0))
            .andExpect(jsonPath("$.meal.alternatives[0]").value("COOK"))
            .andExpect(jsonPath("$.meal.alternatives[1]").value("PICKUP"))
            .andExpect(jsonPath("$.hobby.hobbies[0]").value("READING"))
            .andExpect(jsonPath("$.hobby.spendingTypes[0]").value("GOODS"))
            .andExpect(jsonPath("$.hobby.frequencies[0].spendingType").value("GOODS"))
            .andExpect(jsonPath("$.transport").isEmpty)
            .andReturn().response.contentAsString

        val getJson = getSurvey(token)
            .andExpect(status().isOk)
            .andReturn().response.contentAsString

        assertEquals(jsonObject(putJson), jsonObject(getJson))
    }

    @Test
    fun `second PUT removes omitted categories and their old answer rows`() {
        val token = issueGuestToken()
        putSurvey(token, MEAL_AND_TRANSPORT_REQUEST).andExpect(status().isOk)

        val replacement = """
            {
              "meal": null,
              "transport": null,
              "hobby": null,
              "living": {
                "areas": ["UNKNOWN"],
                "monthlySpendingRange": null,
                "frequencies": [],
                "trigger": "RARELY_UNPLANNED",
                "savingMethods": ["NO_LIVING_MISSION"]
              }
            }
        """.trimIndent()
        val replacementJson = putSurvey(token, replacement)
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.meal").isEmpty)
            .andExpect(jsonPath("$.transport").isEmpty)
            .andExpect(jsonPath("$.living.areas[0]").value("UNKNOWN"))
            .andReturn().response.contentAsString

        val getJson = getSurvey(token).andExpect(status().isOk).andReturn().response.contentAsString
        assertEquals(jsonObject(replacementJson), jsonObject(getJson))
        assertEquals(
            listOf("LIVING"),
            storedCategoryCodes(token),
        )
    }

    @Test
    fun `invalid replacement rolls back and preserves the previous survey`() {
        val token = issueGuestToken()
        val original = putSurvey(token, MEAL_REQUEST)
            .andExpect(status().isOk)
            .andReturn().response.contentAsString
        val originalRowCount = storedAnswerCount(token)

        putSurvey(
            token,
            MEAL_REQUEST.replace("\"weeklyFrequency\": 3", "\"weeklyFrequency\": 8"),
        ).andExpect(status().isBadRequest)
            .andExpect(jsonPath("$.name").value("MISSION_SURVEY_INVALID"))

        val current = getSurvey(token).andExpect(status().isOk).andReturn().response.contentAsString
        assertEquals(jsonObject(original), jsonObject(current))
        assertEquals(originalRowCount, storedAnswerCount(token))
    }

    @Test
    fun `identical PUT is repeatable and keeps one canonical set of rows`() {
        val token = issueGuestToken()
        val first = putSurvey(token, MEAL_AND_TRANSPORT_REQUEST)
            .andExpect(status().isOk)
            .andReturn().response.contentAsString
        val firstRowCount = storedAnswerCount(token)

        val second = putSurvey(token, MEAL_AND_TRANSPORT_REQUEST)
            .andExpect(status().isOk)
            .andReturn().response.contentAsString

        assertEquals(jsonObject(first), jsonObject(second))
        assertEquals(firstRowCount, storedAnswerCount(token))
        assertEquals(1, storedSurveyCount(token))
    }

    @Test
    fun `GET without a stored survey returns not found`() {
        val token = issueGuestToken()

        getSurvey(token)
            .andExpect(status().isNotFound)
            .andExpect(jsonPath("$.name").value("MISSION_SURVEY_NOT_FOUND"))
    }

    @Test
    fun `zero five and duplicate category queries are rejected`() {
        val token = issueGuestToken()

        listOf(
            emptyArray(),
            arrayOf("MEAL", "TRANSPORT", "HOBBY", "LIVING", "MEAL"),
            arrayOf("MEAL", "MEAL"),
        ).forEach { categories ->
            mockMvc.perform(
                get("$SURVEY_PATH/questions")
                    .header("Authorization", "Bearer $token")
                    .param("categories", *categories),
            ).andExpect(status().isBadRequest)
                .andExpect(jsonPath("$.name").value("MISSION_SURVEY_INVALID"))
        }
    }

    @Test
    fun `empty comma separated category segment is rejected`() {
        val token = issueGuestToken()

        mockMvc.perform(
            get("$SURVEY_PATH/questions")
                .header("Authorization", "Bearer $token")
                .param("categories", "MEAL,"),
        ).andExpect(status().isBadRequest)
            .andExpect(jsonPath("$.name").value("MISSION_SURVEY_INVALID"))
    }

    @Test
    fun `invalid branch and frequency combinations are rejected`() {
        val token = issueGuestToken()
        val unknownWithFrequency = """
            {
              "meal": {
                "target": "UNKNOWN",
                "weeklyFrequency": 0,
                "alternatives": ["NO_ALTERNATIVE"],
                "reason": null,
                "exclusions": ["NONE"]
              }
            }
        """.trimIndent()
        val keyedFrequencyMismatch = """
            {
              "hobby": {
                "hobbies": ["READING"],
                "spendingTypes": ["GOODS"],
                "monthlySpendingRange": "UNDER_50K",
                "frequencies": [{"spendingType": "SUBSCRIPTION", "count": 1}],
                "savingMethods": ["WAIT_BEFORE_BUYING"]
              }
            }
        """.trimIndent()
        val doNotReduceWithFollowUp = """
            {
              "hobby": {
                "hobbies": ["READING"],
                "spendingTypes": ["DO_NOT_REDUCE"],
                "monthlySpendingRange": "UNDER_50K",
                "frequencies": [],
                "savingMethods": ["NO_HOBBY_MISSION"]
              }
            }
        """.trimIndent()

        listOf(unknownWithFrequency, keyedFrequencyMismatch, doNotReduceWithFollowUp).forEach { body ->
            putSurvey(token, body)
                .andExpect(status().isBadRequest)
                .andExpect(jsonPath("$.name").value("MISSION_SURVEY_INVALID"))
        }
    }

    @Test
    fun `OpenAPI publishes detailed mission survey request contract`() {
        val document = mockMvc.perform(get("/v3/api-docs"))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.paths['$SURVEY_PATH/questions'].get.responses['200']").exists())
            .andExpect(jsonPath("$.paths['$SURVEY_PATH/questions'].get.responses['400']").exists())
            .andExpect(jsonPath("$.paths['$SURVEY_PATH/questions'].get.responses['401']").exists())
            .andExpect(jsonPath("$.paths['$SURVEY_PATH/questions'].get.responses['500']").exists())
            .andExpect(jsonPath("$.paths['$SURVEY_PATH'].get.responses['200']").exists())
            .andExpect(jsonPath("$.paths['$SURVEY_PATH'].get.responses['401']").exists())
            .andExpect(jsonPath("$.paths['$SURVEY_PATH'].get.responses['404']").exists())
            .andExpect(jsonPath("$.paths['$SURVEY_PATH'].get.responses['500']").exists())
            .andExpect(jsonPath("$.paths['$SURVEY_PATH'].put.responses['200']").exists())
            .andExpect(jsonPath("$.paths['$SURVEY_PATH'].put.responses['400']").exists())
            .andExpect(jsonPath("$.paths['$SURVEY_PATH'].put.responses['401']").exists())
            .andExpect(jsonPath("$.paths['$SURVEY_PATH'].put.responses['409']").exists())
            .andExpect(jsonPath("$.paths['$SURVEY_PATH'].put.responses['500']").exists())
            .andExpect(jsonPath("$.paths['$SURVEY_PATH'].put.security[0].accessTokenAuth").exists())
            .andReturn()
            .response
            .contentAsString

        val examples: Map<String, Any> = JsonPath.read(
            document,
            "$.paths['$SURVEY_PATH'].put.requestBody.content['application/json'].examples",
        )
        assertEquals(
            setOf(
                "allCategories",
                "singleCategory",
                "mealAndTransportUnknown",
                "hobbyDoNotReduce",
                "keyedFrequencies",
            ),
            examples.keys,
        )
        val token = issueGuestToken()
        examples.values.forEach { example ->
            val value = (example as Map<*, *>)["value"]
            putSurvey(token, Configuration.defaultConfiguration().jsonProvider().toJson(value))
                .andExpect(status().isOk)
        }

        val operationDescription: String = JsonPath.read(document, "$.paths['$SURVEY_PATH'].put.description")
        listOf(
            "form state",
            "meal.target이 UNKNOWN",
            "transport.target이 UNKNOWN",
            "hobby.spendingTypes가 [DO_NOT_REDUCE]",
            "living.areas가 [UNKNOWN]",
            "key는 한 번만 등장",
        ).forEach { expected -> assertTrue(operationDescription.contains(expected), expected) }

        val questionCatalog = MissionSurveyQuestionCatalog()
        val documentedOptionPaths = mapOf(
            MissionSurveyQuestionCode.MEAL_TARGET to "$.components.schemas.MealSurveyRequest.properties.target.enum",
            MissionSurveyQuestionCode.MEAL_ALTERNATIVES to "$.components.schemas.MealSurveyRequest.properties.alternatives.items.enum",
            MissionSurveyQuestionCode.MEAL_REASON to "$.components.schemas.MealSurveyRequest.properties.reason.enum",
            MissionSurveyQuestionCode.MEAL_EXCLUSIONS to "$.components.schemas.MealSurveyRequest.properties.exclusions.items.enum",
            MissionSurveyQuestionCode.TRANSPORT_PRIMARY_MODE to "$.components.schemas.TransportSurveyRequest.properties.primaryMode.enum",
            MissionSurveyQuestionCode.TRANSPORT_TARGET to "$.components.schemas.TransportSurveyRequest.properties.target.enum",
            MissionSurveyQuestionCode.TRANSPORT_REASON to "$.components.schemas.TransportSurveyRequest.properties.reason.enum",
            MissionSurveyQuestionCode.TRANSPORT_EXCLUSIONS to "$.components.schemas.TransportSurveyRequest.properties.exclusions.items.enum",
            MissionSurveyQuestionCode.HOBBY_TYPES to "$.components.schemas.HobbySurveyRequest.properties.hobbies.items.enum",
            MissionSurveyQuestionCode.HOBBY_SPENDING_TYPES to "$.components.schemas.HobbySurveyRequest.properties.spendingTypes.items.enum",
            MissionSurveyQuestionCode.HOBBY_MONTHLY_SPENDING to "$.components.schemas.HobbySurveyRequest.properties.monthlySpendingRange.enum",
            MissionSurveyQuestionCode.HOBBY_SAVING_METHODS to "$.components.schemas.HobbySurveyRequest.properties.savingMethods.items.enum",
            MissionSurveyQuestionCode.LIVING_AREAS to "$.components.schemas.LivingSurveyRequest.properties.areas.items.enum",
            MissionSurveyQuestionCode.LIVING_MONTHLY_SPENDING to "$.components.schemas.LivingSurveyRequest.properties.monthlySpendingRange.enum",
            MissionSurveyQuestionCode.LIVING_TRIGGER to "$.components.schemas.LivingSurveyRequest.properties.trigger.enum",
            MissionSurveyQuestionCode.LIVING_SAVING_METHODS to "$.components.schemas.LivingSurveyRequest.properties.savingMethods.items.enum",
        )
        documentedOptionPaths.forEach { (questionCode, path) ->
            val expectedCodes = questionCatalog.question(questionCode).options.map { it.code }
            val documentedCodes: List<String> = JsonPath.read(document, path)
            assertEquals(expectedCodes, documentedCodes, questionCode.code)
        }

        val hobbyFrequencyCodes: List<String> = JsonPath.read(
            document,
            "$.components.schemas.HobbyFrequencyRequest.properties.spendingType.enum",
        )
        assertEquals(
            questionCatalog.question(MissionSurveyQuestionCode.HOBBY_SPENDING_TYPES)
                .options.map { it.code }
                .filterNot { it == "DO_NOT_REDUCE" },
            hobbyFrequencyCodes,
        )
        val livingFrequencyCodes: List<String> = JsonPath.read(
            document,
            "$.components.schemas.LivingFrequencyRequest.properties.area.enum",
        )
        assertEquals(
            questionCatalog.question(MissionSurveyQuestionCode.LIVING_AREAS)
                .options.map { it.code }
                .filterNot { it == "UNKNOWN" },
            livingFrequencyCodes,
        )

        val documentedSelectionPaths = mapOf(
            MissionSurveyQuestionCode.MEAL_ALTERNATIVES to "$.components.schemas.MealSurveyRequest.properties.alternatives",
            MissionSurveyQuestionCode.MEAL_EXCLUSIONS to "$.components.schemas.MealSurveyRequest.properties.exclusions",
            MissionSurveyQuestionCode.TRANSPORT_EXCLUSIONS to "$.components.schemas.TransportSurveyRequest.properties.exclusions",
            MissionSurveyQuestionCode.HOBBY_TYPES to "$.components.schemas.HobbySurveyRequest.properties.hobbies",
            MissionSurveyQuestionCode.HOBBY_SPENDING_TYPES to "$.components.schemas.HobbySurveyRequest.properties.spendingTypes",
            MissionSurveyQuestionCode.HOBBY_SAVING_METHODS to "$.components.schemas.HobbySurveyRequest.properties.savingMethods",
            MissionSurveyQuestionCode.LIVING_AREAS to "$.components.schemas.LivingSurveyRequest.properties.areas",
            MissionSurveyQuestionCode.LIVING_SAVING_METHODS to "$.components.schemas.LivingSurveyRequest.properties.savingMethods",
        )
        documentedSelectionPaths.forEach { (questionCode, path) ->
            val question = questionCatalog.question(questionCode)
            assertEquals(question.minSelections, JsonPath.read(document, "$path.minItems"), "${questionCode.code} minItems")
            assertEquals(question.maxSelections, JsonPath.read(document, "$path.maxItems"), "${questionCode.code} maxItems")
        }

        assertEquals(0, JsonPath.read(document, "$.components.schemas.HobbyFrequencyRequest.properties.count.minimum"))
        assertEquals(31, JsonPath.read(document, "$.components.schemas.HobbyFrequencyRequest.properties.count.maximum"))
        assertEquals(0, JsonPath.read(document, "$.components.schemas.LivingFrequencyRequest.properties.count.minimum"))
        assertEquals(31, JsonPath.read(document, "$.components.schemas.LivingFrequencyRequest.properties.count.maximum"))
        assertEquals(0, JsonPath.read(document, "$.components.schemas.TransportSurveyRequest.properties.weeklyFrequency.minimum"))
        assertEquals(7, JsonPath.read(document, "$.components.schemas.TransportSurveyRequest.properties.weeklyFrequency.maximum"))
        assertEquals(0, JsonPath.read(document, "$.components.schemas.MealSurveyRequest.properties.weeklyFrequency.minimum"))
        assertEquals(14, JsonPath.read(document, "$.components.schemas.MealSurveyRequest.properties.weeklyFrequency.maximum"))

        val mealFrequencyDescription: String = JsonPath.read(
            document,
            "$.components.schemas.MealSurveyRequest.properties.weeklyFrequency.description",
        )
        assertTrue(mealFrequencyDescription.contains("PAID_BEVERAGE는 0~14"))
        val hobbyFrequencyDescription: String = JsonPath.read(
            document,
            "$.components.schemas.HobbySurveyRequest.properties.frequencies.description",
        )
        assertTrue(hobbyFrequencyDescription.contains("spendingTypes와 정확히 일치"))
        val livingCountDescription: String = JsonPath.read(
            document,
            "$.components.schemas.LivingFrequencyRequest.properties.count.description",
        )
        assertTrue(livingCountDescription.contains("SUBSCRIPTION은 구독 개수 0~20"))
    }

    private fun putSurvey(token: String, body: String): ResultActions =
        mockMvc.perform(
            put(SURVEY_PATH)
                .header("Authorization", "Bearer $token")
                .contentType(MediaType.APPLICATION_JSON)
                .content(body),
        )

    private fun getSurvey(token: String): ResultActions =
        mockMvc.perform(get(SURVEY_PATH).header("Authorization", "Bearer $token"))

    private fun issueGuestToken(): String {
        val response = mockMvc.perform(
            post("/api/auth/guest")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""{"uuid":"${UUID.randomUUID()}"}"""),
        ).andExpect(status().isCreated).andReturn().response.contentAsString
        return JsonPath.read(response, "$.accessToken")
    }

    private fun guestUserId(token: String): Long = SignedJWT.parse(token).jwtClaimsSet.subject.toLong()

    private fun storedCategoryCodes(token: String): List<String> =
        queryStrings(
            """
                SELECT DISTINCT answer.category_code
                FROM mission_survey_answer answer
                JOIN mission_survey survey ON survey.id = answer.mission_survey_id
                WHERE survey.guest_user_id = ?
                ORDER BY answer.category_code
            """.trimIndent(),
            guestUserId(token),
        )

    private fun storedAnswerCount(token: String): Int =
        queryInt(
            """
                SELECT COUNT(*)
                FROM mission_survey_answer answer
                JOIN mission_survey survey ON survey.id = answer.mission_survey_id
                WHERE survey.guest_user_id = ?
            """.trimIndent(),
            guestUserId(token),
        )

    private fun storedSurveyCount(token: String): Int =
        queryInt(
            "SELECT COUNT(*) FROM mission_survey WHERE guest_user_id = ?",
            guestUserId(token),
        )

    private fun queryStrings(sql: String, vararg parameters: Any): List<String> =
        dataSource.connection.use { connection ->
            connection.prepareStatement(sql).use { statement ->
                parameters.forEachIndexed { index, value -> statement.setObject(index + 1, value) }
                statement.executeQuery().use { rows ->
                    buildList {
                        while (rows.next()) add(rows.getString(1))
                    }
                }
            }
        }

    private fun queryInt(sql: String, vararg parameters: Any): Int =
        dataSource.connection.use { connection ->
            connection.prepareStatement(sql).use { statement ->
                parameters.forEachIndexed { index, value -> statement.setObject(index + 1, value) }
                statement.executeQuery().use { rows ->
                    check(rows.next())
                    rows.getInt(1)
                }
            }
        }

    private fun jsonObject(json: String): Map<String, Any?> = JsonPath.parse(json).read("$")

    companion object {
        private const val SURVEY_PATH = "/api/missions/surveys"

        private val MEAL_REQUEST = """
            {
              "meal": {
                "target": "DELIVERY",
                "weeklyFrequency": 3,
                "alternatives": ["COOK", "PICKUP"],
                "reason": "TIME_OR_ENERGY",
                "exclusions": ["NONE"]
              },
              "transport": null,
              "hobby": null,
              "living": null
            }
        """.trimIndent()

        private val MEAL_AND_TRANSPORT_REQUEST = """
            {
              "meal": {
                "target": "DELIVERY",
                "weeklyFrequency": 3,
                "alternatives": ["COOK", "PICKUP"],
                "reason": "TIME_OR_ENERGY",
                "exclusions": ["NONE"]
              },
              "transport": {
                "primaryMode": "TAXI",
                "target": "TAXI",
                "weeklyFrequency": 2,
                "reason": "TIME_PRESSURE",
                "exclusions": ["NONE"]
              },
              "hobby": null,
              "living": null
            }
        """.trimIndent()
    }
}

package backend.yapp.api.goal.dto

import io.swagger.v3.core.converter.ModelConverters
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import tools.jackson.databind.ObjectMapper

class GoalStatusResponseTest {
    private val objectMapper = ObjectMapper()

    @Test
    fun `this month response serializes dDay with camel case`() {
        val json = objectMapper.writeValueAsString(
            ThisMonthResponse(
                targetManwon = 100,
                savedManwon = 20,
                progressPercent = 20,
                dDay = 7,
            ),
        )

        assertTrue("\"dDay\":7" in json)
        assertFalse("\"dday\":7" in json)
    }

    @Test
    fun `this month response exposes dDay in OpenAPI schema`() {
        val schema = ModelConverters.getInstance()
            .read(ThisMonthResponse::class.java)
            .getValue("ThisMonthResponse")

        assertTrue("dDay" in schema.properties)
        assertFalse("dday" in schema.properties)
    }
}

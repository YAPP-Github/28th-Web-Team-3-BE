package backend.yapp.infra.mission.generation

import backend.yapp.core.mission.generation.port.MissionBlogSearchOutcome
import backend.yapp.core.mission.generation.port.MissionBlogSearchOutcomeCategory
import ch.qos.logback.classic.Logger
import ch.qos.logback.classic.spi.ILoggingEvent
import ch.qos.logback.core.read.ListAppender
import io.micrometer.core.instrument.simple.SimpleMeterRegistry
import java.time.Duration
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import org.slf4j.LoggerFactory

class NaverBlogSearchTelemetryTest {
    @Test
    fun `records fixed outcome metrics without logging credentials or query`() {
        val logger = LoggerFactory.getLogger(MicrometerNaverBlogSearchTelemetry::class.java) as Logger
        val appender = ListAppender<ILoggingEvent>()
        appender.start()
        logger.addAppender(appender)
        try {
            val registry = SimpleMeterRegistry()
            val telemetry = MicrometerNaverBlogSearchTelemetry(registry)
            telemetry.failed(
                MissionBlogSearchOutcome.Failed(MissionBlogSearchOutcomeCategory.AUTHORIZATION, attempts = 1),
                credentialsConfigured = true,
                duration = Duration.ofMillis(10),
            )

            assertEquals(
                1.0,
                registry.get(MicrometerNaverBlogSearchTelemetry.OUTCOMES)
                    .tag("category", "AUTHORIZATION")
                    .tag("credentialsConfigured", "true")
                    .counter()
                    .count(),
            )
            val messages = appender.list.joinToString("\n") { it.formattedMessage }
            assertFalse(messages.contains("secret"))
            assertFalse(messages.contains("Authorization"))
            assertFalse(messages.contains("query"))
        } finally {
            logger.detachAppender(appender)
            appender.stop()
        }
    }
}

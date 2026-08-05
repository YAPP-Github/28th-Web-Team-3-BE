package backend.yapp.infra.mission.generation

import ch.qos.logback.classic.Logger
import ch.qos.logback.classic.spi.ILoggingEvent
import ch.qos.logback.core.read.ListAppender
import com.google.genai.errors.ApiException
import io.micrometer.core.instrument.simple.SimpleMeterRegistry
import java.net.SocketTimeoutException
import java.time.Duration
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import org.slf4j.LoggerFactory
import org.springframework.ai.retry.NonTransientAiException

class MissionDraftGenerationTelemetryTest {
    @Test
    fun `classifies all failure categories with fixed safe codes`() {
        val failures = listOf(
            MissionDraftGenerationFailureClassifier.classify(ApiException(401, "UNAUTHENTICATED", "secret")),
            MissionDraftGenerationFailureClassifier.classify(ApiException(429, "RESOURCE_EXHAUSTED", "secret")),
            MissionDraftGenerationFailureClassifier.classify(SocketTimeoutException("secret")),
            MissionDraftGenerationFailureClassifier.classify(NonTransientAiException("secret")),
            MissionDraftGenerationFailureClassifier.classify(
                MissionDraftResponseValidationException(
                    MissionDraftValidationRule.RESPONSE_ITEM_COUNT_MISMATCH,
                ),
            ),
            MissionDraftGenerationFailureClassifier.classify(IllegalStateException("secret")),
        )

        assertEquals(
            setOf(
                MissionDraftGenerationFailureCategory.PROVIDER_AUTH_OR_CONFIG,
                MissionDraftGenerationFailureCategory.PROVIDER_QUOTA_OR_RATE_LIMIT,
                MissionDraftGenerationFailureCategory.NETWORK_OR_TIMEOUT,
                MissionDraftGenerationFailureCategory.PROVIDER_RESPONSE_PARSE_OR_SCHEMA,
                MissionDraftGenerationFailureCategory.RESPONSE_BUSINESS_VALIDATION,
                MissionDraftGenerationFailureCategory.UNEXPECTED_INTERNAL,
            ),
            failures.map { it.category }.toSet(),
        )
        assertEquals("PROVIDER_AUTHORIZATION_FAILURE", failures[0].code)
        assertEquals("PROVIDER_RATE_LIMITED", failures[1].code)
        assertEquals("TRANSPORT_FAILURE", failures[2].code)
        assertEquals("STRUCTURED_OUTPUT_CONVERSION_FAILURE", failures[3].code)
        assertEquals("RESPONSE_ITEM_COUNT_MISMATCH", failures[4].code)
        assertEquals("UNEXPECTED_INTERNAL_FAILURE", failures[5].code)
    }

    @Test
    fun `records low cardinality counters timers and validation rule`() {
        val registry = SimpleMeterRegistry()
        val telemetry = MicrometerMissionDraftGenerationTelemetry(registry)
        val validationFailure = MissionDraftGenerationFailureClassifier.classify(
            MissionDraftResponseValidationException(
                MissionDraftValidationRule.RESPONSE_ITEM_COUNT_MISMATCH,
            ),
        )

        telemetry.attempted(candidateCount = 16)
        telemetry.succeeded(candidateCount = 16, duration = Duration.ofMillis(10))
        telemetry.failed(validationFailure, candidateCount = 16, duration = Duration.ofMillis(20))
        telemetry.fallbackUsed(validationFailure, candidateCount = 16)

        assertEquals(1.0, registry.get(MicrometerMissionDraftGenerationTelemetry.AI_ATTEMPTS).counter().count())
        assertEquals(1.0, registry.get(MicrometerMissionDraftGenerationTelemetry.AI_SUCCEEDED).counter().count())
        assertEquals(
            1.0,
            registry.get(MicrometerMissionDraftGenerationTelemetry.AI_FAILURES)
                .tag("category", "RESPONSE_BUSINESS_VALIDATION")
                .tag("code", "RESPONSE_ITEM_COUNT_MISMATCH")
                .counter()
                .count(),
        )
        assertEquals(
            1.0,
            registry.get(MicrometerMissionDraftGenerationTelemetry.VALIDATION_FAILURES)
                .tag("rule", "RESPONSE_ITEM_COUNT_MISMATCH")
                .counter()
                .count(),
        )
        assertEquals(
            1.0,
            registry.get(MicrometerMissionDraftGenerationTelemetry.FALLBACKS)
                .tag("reason_category", "RESPONSE_BUSINESS_VALIDATION")
                .counter()
                .count(),
        )
        assertEquals(
            2L,
            registry.find(MicrometerMissionDraftGenerationTelemetry.AI_DURATION).timers().sumOf { it.count() },
        )
    }

    @Test
    fun `does not log exception messages or secrets`() {
        val logger = LoggerFactory.getLogger(MicrometerMissionDraftGenerationTelemetry::class.java) as Logger
        val appender = ListAppender<ILoggingEvent>()
        appender.start()
        logger.addAppender(appender)
        try {
            val failure = MissionDraftGenerationFailureClassifier.classify(
                IllegalStateException("GOOGLE_GENAI_API_KEY=should-not-appear"),
            )

            MicrometerMissionDraftGenerationTelemetry(SimpleMeterRegistry()).failed(
                failure,
                candidateCount = 16,
                duration = Duration.ZERO,
            )

            val messages = appender.list.joinToString("\n") { it.formattedMessage }
            assertFalse(messages.contains("GOOGLE_GENAI_API_KEY"))
            assertFalse(messages.contains("should-not-appear"))
            assertFalse(messages.contains("candidateCount"))
        } finally {
            logger.detachAppender(appender)
            appender.stop()
        }
    }
}

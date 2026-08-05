package backend.yapp.infra.mission.generation

import com.google.genai.errors.ApiException
import com.google.genai.errors.GenAiIOException
import com.google.genai.errors.ServerException
import com.fasterxml.jackson.core.JacksonException
import io.micrometer.core.instrument.MeterRegistry
import io.micrometer.core.instrument.Timer
import java.io.IOException
import java.net.SocketTimeoutException
import java.time.Duration
import org.slf4j.LoggerFactory
import org.springframework.ai.retry.NonTransientAiException
import org.springframework.ai.retry.TransientAiException

enum class MissionDraftGenerationFailureCategory {
    PROVIDER_AUTH_OR_CONFIG,
    PROVIDER_QUOTA_OR_RATE_LIMIT,
    NETWORK_OR_TIMEOUT,
    PROVIDER_RESPONSE_PARSE_OR_SCHEMA,
    RESPONSE_BUSINESS_VALIDATION,
    UNEXPECTED_INTERNAL,
}

data class MissionDraftGenerationFailure(
    val category: MissionDraftGenerationFailureCategory,
    val code: String,
    val retryable: Boolean,
    val exceptionFamily: String,
    val providerStatusFamily: String? = null,
)

enum class MissionDraftValidationRule {
    CANDIDATE_TEMPLATE_ID_DUPLICATED,
    RESPONSE_ITEM_COUNT_MISMATCH,
    RESPONSE_TEMPLATE_ID_DUPLICATED,
    RESPONSE_TEMPLATE_ID_SET_MISMATCH,
    RESPONSE_COPY_TEXT_CONSTRAINT_VIOLATED,
}

class MissionDraftResponseValidationException(
    val rule: MissionDraftValidationRule,
) : RuntimeException("Mission draft response validation failed: $rule")

object MissionDraftGenerationFailureClassifier {
    fun classify(error: Throwable): MissionDraftGenerationFailure {
        val causes = error.causes()
        causes.filterIsInstance<MissionDraftResponseValidationException>().firstOrNull()?.let { validation ->
            return MissionDraftGenerationFailure(
                category = MissionDraftGenerationFailureCategory.RESPONSE_BUSINESS_VALIDATION,
                code = validation.rule.name,
                retryable = false,
                exceptionFamily = "VALIDATION",
            )
        }

        causes.filterIsInstance<ApiException>().firstOrNull()?.let { providerError ->
            return providerFailure(providerError)
        }

        if (causes.any { it is GenAiIOException || it is SocketTimeoutException || it is IOException || it is TransientAiException }) {
            return MissionDraftGenerationFailure(
                category = MissionDraftGenerationFailureCategory.NETWORK_OR_TIMEOUT,
                code = "TRANSPORT_FAILURE",
                retryable = true,
                exceptionFamily = "TRANSPORT",
            )
        }

        if (causes.any { it is JacksonException || it is NonTransientAiException }) {
            return MissionDraftGenerationFailure(
                category = MissionDraftGenerationFailureCategory.PROVIDER_RESPONSE_PARSE_OR_SCHEMA,
                code = "STRUCTURED_OUTPUT_CONVERSION_FAILURE",
                retryable = false,
                exceptionFamily = "STRUCTURED_OUTPUT",
            )
        }

        return MissionDraftGenerationFailure(
            category = MissionDraftGenerationFailureCategory.UNEXPECTED_INTERNAL,
            code = "UNEXPECTED_INTERNAL_FAILURE",
            retryable = false,
            exceptionFamily = "INTERNAL",
        )
    }

    private fun providerFailure(error: ApiException): MissionDraftGenerationFailure =
        when (error.code()) {
            401, 403 -> MissionDraftGenerationFailure(
                category = MissionDraftGenerationFailureCategory.PROVIDER_AUTH_OR_CONFIG,
                code = "PROVIDER_AUTHORIZATION_FAILURE",
                retryable = false,
                exceptionFamily = "PROVIDER_API",
                providerStatusFamily = "4xx",
            )

            429 -> MissionDraftGenerationFailure(
                category = MissionDraftGenerationFailureCategory.PROVIDER_QUOTA_OR_RATE_LIMIT,
                code = "PROVIDER_RATE_LIMITED",
                retryable = true,
                exceptionFamily = "PROVIDER_API",
                providerStatusFamily = "4xx",
            )

            408 -> MissionDraftGenerationFailure(
                category = MissionDraftGenerationFailureCategory.NETWORK_OR_TIMEOUT,
                code = "PROVIDER_TIMEOUT",
                retryable = true,
                exceptionFamily = "PROVIDER_API",
                providerStatusFamily = "4xx",
            )

            else -> when {
                error is ServerException || error.code() >= 500 -> MissionDraftGenerationFailure(
                    category = MissionDraftGenerationFailureCategory.NETWORK_OR_TIMEOUT,
                    code = "PROVIDER_SERVER_FAILURE",
                    retryable = true,
                    exceptionFamily = "PROVIDER_API",
                    providerStatusFamily = "5xx",
                )

                else -> MissionDraftGenerationFailure(
                    category = MissionDraftGenerationFailureCategory.PROVIDER_AUTH_OR_CONFIG,
                    code = "PROVIDER_REQUEST_CONFIGURATION_FAILURE",
                    retryable = false,
                    exceptionFamily = "PROVIDER_API",
                    providerStatusFamily = "4xx",
                )
            }
        }

    private fun Throwable.causes(): Sequence<Throwable> =
        generateSequence(this) { current -> current.cause }
}

interface MissionDraftGenerationTelemetry {
    fun attempted(candidateCount: Int)

    fun succeeded(candidateCount: Int, duration: Duration)

    fun failed(failure: MissionDraftGenerationFailure, candidateCount: Int, duration: Duration)

    fun fallbackUsed(failure: MissionDraftGenerationFailure, candidateCount: Int)
}

object NoopMissionDraftGenerationTelemetry : MissionDraftGenerationTelemetry {
    override fun attempted(candidateCount: Int) = Unit

    override fun succeeded(candidateCount: Int, duration: Duration) = Unit

    override fun failed(failure: MissionDraftGenerationFailure, candidateCount: Int, duration: Duration) = Unit

    override fun fallbackUsed(failure: MissionDraftGenerationFailure, candidateCount: Int) = Unit
}

class MicrometerMissionDraftGenerationTelemetry(
    private val meterRegistry: MeterRegistry,
) : MissionDraftGenerationTelemetry {
    override fun attempted(candidateCount: Int) {
        meterRegistry.counter(AI_ATTEMPTS).increment()
    }

    override fun succeeded(candidateCount: Int, duration: Duration) {
        meterRegistry.counter(AI_SUCCEEDED).increment()
        recordDuration(OUTCOME_SUCCEEDED, NO_FAILURE_CATEGORY, duration)
    }

    override fun failed(failure: MissionDraftGenerationFailure, candidateCount: Int, duration: Duration) {
        meterRegistry.counter(
            AI_FAILURES,
            "category", failure.category.name,
            "code", failure.code,
            "retryable", failure.retryable.toString(),
        ).increment()
        if (failure.category == MissionDraftGenerationFailureCategory.RESPONSE_BUSINESS_VALIDATION) {
            meterRegistry.counter(VALIDATION_FAILURES, "rule", failure.code).increment()
        }
        recordDuration(OUTCOME_FAILED, failure.category.name, duration)
        logFailure(failure)
    }

    override fun fallbackUsed(failure: MissionDraftGenerationFailure, candidateCount: Int) {
        meterRegistry.counter(FALLBACKS, "reason_category", failure.category.name).increment()
    }

    private fun recordDuration(outcome: String, category: String, duration: Duration) {
        Timer.builder(AI_DURATION)
            .tag("outcome", outcome)
            .tag("category", category)
            .register(meterRegistry)
            .record(duration)
    }

    private fun logFailure(failure: MissionDraftGenerationFailure) {
        if (failure.category == MissionDraftGenerationFailureCategory.UNEXPECTED_INTERNAL) {
            log.error(
                "mission_generation.ai.failed category={} code={} retryable={} exceptionFamily={} providerStatusFamily={}",
                failure.category,
                failure.code,
                failure.retryable,
                failure.exceptionFamily,
                failure.providerStatusFamily ?: "none",
            )
        } else {
            log.warn(
                "mission_generation.ai.failed category={} code={} retryable={} exceptionFamily={} providerStatusFamily={}",
                failure.category,
                failure.code,
                failure.retryable,
                failure.exceptionFamily,
                failure.providerStatusFamily ?: "none",
            )
        }
    }

    companion object {
        private val log = LoggerFactory.getLogger(MicrometerMissionDraftGenerationTelemetry::class.java)

        const val AI_ATTEMPTS = "mission_generation_ai_attempts_total"
        const val AI_SUCCEEDED = "mission_generation_ai_succeeded_total"
        const val AI_FAILURES = "mission_generation_ai_failures_total"
        const val FALLBACKS = "mission_generation_fallbacks_total"
        const val VALIDATION_FAILURES = "mission_generation_validation_failures_total"
        const val AI_DURATION = "mission_generation_ai_duration_seconds"

        private const val OUTCOME_SUCCEEDED = "succeeded"
        private const val OUTCOME_FAILED = "failed"
        private const val NO_FAILURE_CATEGORY = "none"
    }
}

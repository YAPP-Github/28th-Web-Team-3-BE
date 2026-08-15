package backend.yapp.infra.mission.generation

import backend.yapp.core.mission.generation.port.MissionBlogSearchOutcome
import io.micrometer.core.instrument.MeterRegistry
import io.micrometer.core.instrument.Timer
import java.time.Duration
import org.slf4j.LoggerFactory

interface NaverBlogSearchTelemetry {
    fun completed(
        outcome: MissionBlogSearchOutcome.Completed,
        attempts: Int,
        duration: Duration,
    )

    fun failed(
        outcome: MissionBlogSearchOutcome.Failed,
        credentialsConfigured: Boolean,
        duration: Duration,
        cause: Throwable? = null,
    )
}

object NoopNaverBlogSearchTelemetry : NaverBlogSearchTelemetry {
    override fun completed(outcome: MissionBlogSearchOutcome.Completed, attempts: Int, duration: Duration) = Unit

    override fun failed(
        outcome: MissionBlogSearchOutcome.Failed,
        credentialsConfigured: Boolean,
        duration: Duration,
        cause: Throwable?,
    ) = Unit
}

class MicrometerNaverBlogSearchTelemetry(
    private val meterRegistry: MeterRegistry,
) : NaverBlogSearchTelemetry {
    override fun completed(
        outcome: MissionBlogSearchOutcome.Completed,
        attempts: Int,
        duration: Duration,
    ) {
        meterRegistry.counter(OUTCOMES, "category", outcome.category.name).increment()
        Timer.builder(DURATION)
            .tag("outcome", outcome.category.name)
            .register(meterRegistry)
            .record(duration)
        log.info(
            "mission_generation.naver_blog_search.completed category={} attemptCount={} providerItemCount={} normalizedResultCount={}",
            outcome.category,
            attempts,
            outcome.providerItemCount,
            outcome.results.size,
        )
    }

    override fun failed(
        outcome: MissionBlogSearchOutcome.Failed,
        credentialsConfigured: Boolean,
        duration: Duration,
        cause: Throwable?,
    ) {
        meterRegistry.counter(
            OUTCOMES,
            "category", outcome.category.name,
            "credentialsConfigured", credentialsConfigured.toString(),
        ).increment()
        Timer.builder(DURATION)
            .tag("outcome", outcome.category.name)
            .register(meterRegistry)
            .record(duration)
        val rootCause = generateSequence(cause) { it.cause }.lastOrNull()
        log.warn(
            "mission_generation.naver_blog_search.failed category={} attemptCount={} credentialsConfigured={} " +
                "exceptionType={} rootCauseType={}",
            outcome.category,
            outcome.attempts,
            credentialsConfigured,
            cause?.javaClass?.simpleName ?: NONE,
            rootCause?.javaClass?.simpleName ?: NONE,
        )
    }

    companion object {
        private val log = LoggerFactory.getLogger(MicrometerNaverBlogSearchTelemetry::class.java)

        const val OUTCOMES = "mission_generation_naver_blog_search_outcomes_total"
        const val DURATION = "mission_generation_naver_blog_search_duration_seconds"
        private const val NONE = "NONE"
    }
}

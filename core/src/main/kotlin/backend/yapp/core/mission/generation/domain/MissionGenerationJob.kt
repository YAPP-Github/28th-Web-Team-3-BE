package backend.yapp.core.mission.generation.domain

import backend.yapp.core.mission.generation.port.MissionDraftGenerationSource
import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.EnumType
import jakarta.persistence.Enumerated
import jakarta.persistence.Id
import jakarta.persistence.Table
import jakarta.persistence.Version
import java.time.Instant
import java.time.Duration
import java.util.UUID

@Entity
@Table(name = "mission_generation_job")
class MissionGenerationJob(
    @Id
    val id: UUID,
    @Column(name = "guest_user_id", nullable = false)
    val guestUserId: Long,
    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    var status: MissionGenerationJobStatus = MissionGenerationJobStatus.PENDING,
    @Column(name = "active_generation_key", length = 20)
    var activeGenerationKey: String? = ACTIVE_KEY,
    @Column(name = "failure_code", length = 80)
    var failureCode: String? = null,
    @Enumerated(EnumType.STRING)
    @Column(name = "generation_source", length = 30)
    var generationSource: MissionDraftGenerationSource? = null,
    @Column(name = "expires_at")
    var expiresAt: Instant? = null,
    @Column(name = "confirmation_fingerprint", length = 64)
    var confirmationFingerprint: String? = null,
    @Column(name = "confirmed_at")
    var confirmedAt: Instant? = null,
    @Column(name = "created_at", nullable = false)
    val createdAt: Instant,
    @Column(name = "updated_at", nullable = false)
    var updatedAt: Instant = createdAt,
    @Version
    @Column(name = "version", nullable = false)
    var version: Long = 0,
    @Column(name = "attempt_count", nullable = false)
    var attemptCount: Int = 0,
    @Column(name = "lease_token")
    var leaseToken: UUID? = null,
    @Column(name = "lease_expires_at")
    var leaseExpiresAt: Instant? = null,
) {
    fun start(now: Instant): Boolean = claim(now, UUID.randomUUID(), java.time.Duration.ofMinutes(10))

    fun claim(now: Instant, leaseToken: UUID, leaseDuration: java.time.Duration): Boolean {
        if (status == MissionGenerationJobStatus.RUNNING && leaseExpiresAt?.isAfter(now) == true) return false
        if (status != MissionGenerationJobStatus.PENDING && status != MissionGenerationJobStatus.RUNNING) return false
        status = MissionGenerationJobStatus.RUNNING
        this.leaseToken = leaseToken
        leaseExpiresAt = now.plus(leaseDuration)
        attemptCount++
        updatedAt = now
        return true
    }

    fun ownsLease(token: UUID, now: Instant): Boolean =
        status == MissionGenerationJobStatus.RUNNING && leaseToken == token && leaseExpiresAt?.isAfter(now) == true

    fun releaseOrFail(token: UUID, now: Instant, maxAttempts: Int): Boolean {
        if (status != MissionGenerationJobStatus.RUNNING || leaseToken != token) return false
        if (attemptCount >= maxAttempts) {
            fail("MISSION_GENERATION_RETRY_EXHAUSTED", now)
            return true
        }
        status = MissionGenerationJobStatus.PENDING
        leaseToken = null
        leaseExpiresAt = null
        updatedAt = now
        return true
    }

    fun retryOrFail(now: Instant, maxAttempts: Int): Boolean {
        if (status != MissionGenerationJobStatus.RUNNING || leaseExpiresAt?.isAfter(now) == true) return false
        if (attemptCount >= maxAttempts) {
            fail("MISSION_GENERATION_RETRY_EXHAUSTED", now)
            return false
        }
        status = MissionGenerationJobStatus.PENDING
        leaseToken = null
        leaseExpiresAt = null
        updatedAt = now
        return true
    }

    fun succeed(now: Instant, expiresAt: Instant, generationSource: MissionDraftGenerationSource) {
        check(status == MissionGenerationJobStatus.RUNNING)
        status = MissionGenerationJobStatus.SUCCEEDED
        activeGenerationKey = null
        this.expiresAt = expiresAt
        this.generationSource = generationSource
        leaseToken = null
        leaseExpiresAt = null
        updatedAt = now
    }

    fun fail(code: String, now: Instant) {
        if (status == MissionGenerationJobStatus.SUCCEEDED || status == MissionGenerationJobStatus.FAILED) return
        status = MissionGenerationJobStatus.FAILED
        activeGenerationKey = null
        failureCode = code
        leaseToken = null
        leaseExpiresAt = null
        updatedAt = now
    }

    fun confirm(fingerprint: String, now: Instant): ConfirmationResult {
        val existing = confirmationFingerprint
        if (existing == null) {
            confirmationFingerprint = fingerprint
            confirmedAt = now
            updatedAt = now
            return ConfirmationResult.CREATED
        }
        return if (existing == fingerprint) ConfirmationResult.IDEMPOTENT else ConfirmationResult.CONFLICT
    }

    fun isExpired(now: Instant): Boolean = expiresAt?.let { !now.isBefore(it) } ?: true

    companion object {
        const val ACTIVE_KEY = "ACTIVE"
    }
}

@Entity
@Table(name = "mission_generation_outbox")
class MissionGenerationOutbox(
    @Id val id: UUID,
    @Column(name = "job_id", nullable = false) val jobId: UUID,
    @Column(nullable = false) val generation: Int = 1,
    @Enumerated(EnumType.STRING) @Column(nullable = false, length = 20) var status: MissionGenerationOutboxStatus = MissionGenerationOutboxStatus.CREATED,
    @Column(name = "next_attempt_at", nullable = false) var nextAttemptAt: Instant,
    @Column(name = "claimed_at") var claimedAt: Instant? = null,
    @Column(name = "claim_token") var claimToken: UUID? = null,
    @Column(name = "published_at") var publishedAt: Instant? = null,
    @Column(name = "task_name", length = 180) var taskName: String? = null,
    @Column(name = "last_error", length = 500) var lastError: String? = null,
    @Column(name = "created_at", nullable = false) val createdAt: Instant,
    @Column(name = "updated_at", nullable = false) var updatedAt: Instant = createdAt,
) {
    fun claim(now: Instant, claimTimeout: Duration, token: UUID): Boolean {
        if (status == MissionGenerationOutboxStatus.PUBLISHED || nextAttemptAt.isAfter(now)) return false
        status = MissionGenerationOutboxStatus.CLAIMED
        claimedAt = now
        claimToken = token
        nextAttemptAt = now.plus(claimTimeout)
        lastError = null
        updatedAt = now
        return true
    }

    fun published(token: UUID, taskName: String, now: Instant): Boolean {
        if (status != MissionGenerationOutboxStatus.CLAIMED || claimToken != token) return false
        status = MissionGenerationOutboxStatus.PUBLISHED
        this.taskName = taskName
        publishedAt = now
        updatedAt = now
        return true
    }

    fun retry(token: UUID, error: String, now: Instant, delay: Duration): Boolean {
        if (status == MissionGenerationOutboxStatus.PUBLISHED || claimToken != token) return false
        status = MissionGenerationOutboxStatus.CREATED
        claimToken = null
        lastError = error.take(500)
        nextAttemptAt = now.plus(delay)
        updatedAt = now
        return true
    }
}

enum class MissionGenerationOutboxStatus { CREATED, CLAIMED, PUBLISHED }

enum class MissionGenerationJobStatus {
    PENDING,
    RUNNING,
    SUCCEEDED,
    FAILED,
}

enum class ConfirmationResult {
    CREATED,
    IDEMPOTENT,
    CONFLICT,
}

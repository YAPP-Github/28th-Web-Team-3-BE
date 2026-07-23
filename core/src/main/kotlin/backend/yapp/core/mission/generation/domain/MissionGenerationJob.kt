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
) {
    fun start(now: Instant): Boolean {
        if (status != MissionGenerationJobStatus.PENDING) return false
        status = MissionGenerationJobStatus.RUNNING
        updatedAt = now
        return true
    }

    fun succeed(now: Instant, expiresAt: Instant, generationSource: MissionDraftGenerationSource) {
        check(status == MissionGenerationJobStatus.RUNNING)
        status = MissionGenerationJobStatus.SUCCEEDED
        activeGenerationKey = null
        this.expiresAt = expiresAt
        this.generationSource = generationSource
        updatedAt = now
    }

    fun fail(code: String, now: Instant) {
        if (status == MissionGenerationJobStatus.SUCCEEDED || status == MissionGenerationJobStatus.FAILED) return
        status = MissionGenerationJobStatus.FAILED
        activeGenerationKey = null
        failureCode = code
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

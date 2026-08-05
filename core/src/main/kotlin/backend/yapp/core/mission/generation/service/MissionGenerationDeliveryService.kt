package backend.yapp.core.mission.generation.service

import backend.yapp.core.mission.generation.domain.MissionGenerationJobRepository
import backend.yapp.core.mission.generation.domain.MissionGenerationJobStatus
import backend.yapp.core.mission.generation.domain.MissionGenerationOutbox
import backend.yapp.core.mission.generation.domain.MissionGenerationOutboxRepository
import backend.yapp.core.mission.generation.port.MissionGenerationTaskPublisher
import java.time.Clock
import java.time.Duration
import java.time.Instant
import java.util.UUID
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Propagation
import org.springframework.transaction.annotation.Transactional

@Service
class MissionGenerationDispatchService(
    private val transactions: MissionGenerationDeliveryTransactions,
    private val leaseRecovery: MissionGenerationLeaseRecoveryService,
    private val publisher: MissionGenerationTaskPublisher,
) {
    fun dispatch(): MissionGenerationDispatchResult {
        val recovery = leaseRecovery.reconcileExpiredLeases()
        var published = 0
        var failed = 0
        transactions.claimDue().forEach { task ->
            try {
                val taskName = publisher.publish(task.jobId, task.generation)
                transactions.markPublished(task.id, task.claimToken, taskName)
                published++
            } catch (exception: Exception) {
                transactions.markRetry(task.id, task.claimToken, exception.message ?: exception.javaClass.simpleName)
                failed++
            }
        }
        return MissionGenerationDispatchResult(published, failed, recovery.requeued, recovery.failed)
    }
}

@Service
class MissionGenerationDeliveryTransactions(
    private val outboxRepository: MissionGenerationOutboxRepository,
    private val clock: Clock,
) {
    @Transactional
    fun claimDue(): List<MissionGenerationOutboxTask> {
        val now = clock.instant()
        return outboxRepository.findDueForUpdate(now).mapNotNull { outbox ->
            val token = UUID.randomUUID()
            if (!outbox.claim(now, OUTBOX_CLAIM_TIMEOUT, token)) null else {
                MissionGenerationOutboxTask(outbox.id, outbox.jobId, outbox.generation, token)
            }
        }
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    fun markPublished(id: UUID, claimToken: UUID, taskName: String) {
        outboxRepository.findByIdForUpdate(id)?.published(claimToken, taskName, clock.instant())
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    fun markRetry(id: UUID, claimToken: UUID, error: String) {
        outboxRepository.findByIdForUpdate(id)?.retry(claimToken, error, clock.instant(), OUTBOX_RETRY_DELAY)
    }

    companion object {
        private val OUTBOX_CLAIM_TIMEOUT = Duration.ofMinutes(2)
        private val OUTBOX_RETRY_DELAY = Duration.ofSeconds(10)
    }
}

@Service
class MissionGenerationLeaseRecoveryService(
    private val jobRepository: MissionGenerationJobRepository,
    private val transaction: MissionGenerationLeaseRecoveryTransaction,
    private val clock: Clock,
) {
    fun reconcileExpiredLeases(): MissionGenerationRecoveryResult {
        val now = clock.instant()
        var requeued = 0
        var failed = 0
        jobRepository.findRecoverableRunningIds(now).forEach { jobId ->
            when (runCatching { transaction.reconcile(jobId, now) }.getOrDefault(RecoveryAction.NONE)) {
                RecoveryAction.REQUEUED -> requeued++
                RecoveryAction.FAILED -> failed++
                RecoveryAction.NONE -> Unit
            }
        }
        return MissionGenerationRecoveryResult(requeued, failed)
    }
}

@Service
class MissionGenerationLeaseRecoveryTransaction(
    private val jobRepository: MissionGenerationJobRepository,
    private val outboxRepository: MissionGenerationOutboxRepository,
) {
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    fun reconcile(jobId: UUID, now: Instant): RecoveryAction {
        val job = jobRepository.findByIdForUpdate(jobId) ?: return RecoveryAction.NONE
        val attemptsBefore = job.attemptCount
        if (!job.retryOrFail(now, MAX_ATTEMPTS)) return if (
            attemptsBefore >= MAX_ATTEMPTS && job.status == MissionGenerationJobStatus.FAILED
        ) RecoveryAction.FAILED else RecoveryAction.NONE

        val generation = (outboxRepository.findTopByJobIdOrderByGenerationDesc(jobId)?.generation ?: 0) + 1
        outboxRepository.save(
            MissionGenerationOutbox(
                id = UUID.randomUUID(),
                jobId = jobId,
                generation = generation,
                nextAttemptAt = now,
                createdAt = now,
            ),
        )
        return RecoveryAction.REQUEUED
    }

    companion object {
        private const val MAX_ATTEMPTS = 5
    }
}

data class MissionGenerationOutboxTask(val id: UUID, val jobId: UUID, val generation: Int, val claimToken: UUID)
data class MissionGenerationDispatchResult(val published: Int, val publishFailed: Int, val requeued: Int, val terminalFailed: Int)
data class MissionGenerationRecoveryResult(val requeued: Int, val failed: Int)
enum class RecoveryAction { NONE, REQUEUED, FAILED }

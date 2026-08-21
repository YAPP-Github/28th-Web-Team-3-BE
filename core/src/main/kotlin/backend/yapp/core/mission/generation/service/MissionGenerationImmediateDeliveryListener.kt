package backend.yapp.core.mission.generation.service

import backend.yapp.core.mission.generation.port.MissionGenerationTaskPublisher
import java.util.UUID
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.stereotype.Component
import org.springframework.stereotype.Service
import org.springframework.transaction.event.TransactionPhase
import org.springframework.transaction.event.TransactionalEventListener

data class MissionGenerationOutboxCreatedEvent(
    val outboxId: UUID,
)

@Component
@ConditionalOnProperty(
    prefix = "mission.generation.immediate-delivery",
    name = ["enabled"],
    havingValue = "true",
)
class MissionGenerationImmediateDeliveryListener(
    private val delivery: MissionGenerationImmediateDeliveryService,
) {
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    fun on(event: MissionGenerationOutboxCreatedEvent) {
        delivery.deliver(event.outboxId)
    }
}

@Service
class MissionGenerationImmediateDeliveryService(
    private val transactions: MissionGenerationDeliveryTransactions,
    private val publisher: MissionGenerationTaskPublisher,
) {
    fun deliver(outboxId: UUID) {
        val task = runCatching { transactions.claimById(outboxId) }.getOrNull() ?: return
        try {
            val taskName = publisher.publish(task.jobId, task.generation)
            transactions.markPublished(task.id, task.claimToken, taskName)
        } catch (exception: Exception) {
            runCatching {
                transactions.markRetry(task.id, task.claimToken, failureCode(exception))
            }
        }
    }

    private fun failureCode(exception: Exception): String =
        if (exception is java.util.concurrent.TimeoutException || exception.javaClass.simpleName == "DeadlineExceededException") {
            TIMEOUT
        } else {
            PUBLISH_FAILED
        }

    companion object {
        private const val TIMEOUT = "TASK_PUBLISH_TIMEOUT"
        private const val PUBLISH_FAILED = "TASK_PUBLISH_FAILED"
    }
}

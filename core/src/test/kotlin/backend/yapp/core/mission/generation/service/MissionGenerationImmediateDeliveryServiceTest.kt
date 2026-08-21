package backend.yapp.core.mission.generation.service

import backend.yapp.core.mission.generation.port.MissionGenerationTaskPublisher
import java.util.UUID
import kotlin.test.Test
import org.mockito.Mockito.mock
import org.mockito.Mockito.never
import org.mockito.Mockito.verify
import org.mockito.Mockito.verifyNoInteractions
import org.mockito.Mockito.`when`

class MissionGenerationImmediateDeliveryServiceTest {
    @Test
    fun `committed outbox is published once after its one-row claim`() {
        val transactions = mock(MissionGenerationDeliveryTransactions::class.java)
        val publisher = mock(MissionGenerationTaskPublisher::class.java)
        val outboxId = UUID.randomUUID()
        val task = MissionGenerationOutboxTask(outboxId, UUID.randomUUID(), 1, UUID.randomUUID())
        `when`(transactions.claimById(outboxId)).thenReturn(task)
        `when`(publisher.publish(task.jobId, task.generation)).thenReturn("tasks/mission")

        MissionGenerationImmediateDeliveryService(transactions, publisher).deliver(outboxId)

        verify(transactions).markPublished(task.id, task.claimToken, "tasks/mission")
    }

    @Test
    fun `publish failure stays in outbox retry without propagating`() {
        val transactions = mock(MissionGenerationDeliveryTransactions::class.java)
        val publisher = mock(MissionGenerationTaskPublisher::class.java)
        val outboxId = UUID.randomUUID()
        val task = MissionGenerationOutboxTask(outboxId, UUID.randomUUID(), 1, UUID.randomUUID())
        `when`(transactions.claimById(outboxId)).thenReturn(task)
        `when`(publisher.publish(task.jobId, task.generation)).thenThrow(IllegalStateException("unavailable"))

        MissionGenerationImmediateDeliveryService(transactions, publisher).deliver(outboxId)

        verify(transactions).markRetry(task.id, task.claimToken, "TASK_PUBLISH_FAILED")
    }

    @Test
    fun `timeout is recorded with a bounded timeout code`() {
        val transactions = mock(MissionGenerationDeliveryTransactions::class.java)
        val publisher = mock(MissionGenerationTaskPublisher::class.java)
        val outboxId = UUID.randomUUID()
        val task = MissionGenerationOutboxTask(outboxId, UUID.randomUUID(), 1, UUID.randomUUID())
        `when`(transactions.claimById(outboxId)).thenReturn(task)
        `when`(publisher.publish(task.jobId, task.generation)).thenThrow(DeadlineExceededException())

        MissionGenerationImmediateDeliveryService(transactions, publisher).deliver(outboxId)

        verify(transactions).markRetry(task.id, task.claimToken, "TASK_PUBLISH_TIMEOUT")
    }

    @Test
    fun `retry persistence failure remains isolated from the committed API request`() {
        val transactions = mock(MissionGenerationDeliveryTransactions::class.java)
        val publisher = mock(MissionGenerationTaskPublisher::class.java)
        val outboxId = UUID.randomUUID()
        val task = MissionGenerationOutboxTask(outboxId, UUID.randomUUID(), 1, UUID.randomUUID())
        `when`(transactions.claimById(outboxId)).thenReturn(task)
        `when`(publisher.publish(task.jobId, task.generation)).thenThrow(IllegalStateException("unavailable"))
        org.mockito.Mockito.doThrow(IllegalStateException("database unavailable"))
            .`when`(transactions)
            .markRetry(task.id, task.claimToken, "TASK_PUBLISH_FAILED")

        MissionGenerationImmediateDeliveryService(transactions, publisher).deliver(outboxId)
    }

    @Test
    fun `unclaimable outbox does not invoke the publisher`() {
        val transactions = mock(MissionGenerationDeliveryTransactions::class.java)
        val publisher = mock(MissionGenerationTaskPublisher::class.java)
        val outboxId = UUID.randomUUID()
        `when`(transactions.claimById(outboxId)).thenReturn(null)

        MissionGenerationImmediateDeliveryService(transactions, publisher).deliver(outboxId)

        verifyNoInteractions(publisher)
    }

    @Test
    fun `claim persistence failure remains isolated from the committed API request`() {
        val transactions = mock(MissionGenerationDeliveryTransactions::class.java)
        val publisher = mock(MissionGenerationTaskPublisher::class.java)
        val outboxId = UUID.randomUUID()
        `when`(transactions.claimById(outboxId)).thenThrow(IllegalStateException("database unavailable"))

        MissionGenerationImmediateDeliveryService(transactions, publisher).deliver(outboxId)

        verifyNoInteractions(publisher)
    }

    private class DeadlineExceededException : RuntimeException()
}

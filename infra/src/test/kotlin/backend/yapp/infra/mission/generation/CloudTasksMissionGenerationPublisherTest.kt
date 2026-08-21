package backend.yapp.infra.mission.generation

import com.google.cloud.tasks.v2.CloudTasksClient
import com.google.cloud.tasks.v2.QueueName
import com.google.cloud.tasks.v2.Task
import java.util.UUID
import kotlin.test.Test
import kotlin.test.assertEquals
import org.mockito.ArgumentCaptor
import org.mockito.Mockito.mock
import org.mockito.Mockito.verify

class CloudTasksMissionGenerationPublisherTest {
    @Test
    fun `publishes a deterministically named OIDC task to the private worker`() {
        val client = mock(CloudTasksClient::class.java)
        val publisher = CloudTasksMissionGenerationPublisher(
            client,
            DeliveryProperties(
                enabled = true,
                projectId = "test-project",
                location = "asia-northeast3",
                queue = "mission-generation",
                workerUrl = "https://worker.example.test",
                oidcServiceAccount = "tasks-invoker@example.test",
            ),
        )
        val jobId = UUID.randomUUID()

        val taskName = publisher.publish(jobId, 2)

        val task = ArgumentCaptor.forClass(Task::class.java)
        verify(client).createTask(
            org.mockito.ArgumentMatchers.eq(QueueName.of("test-project", "asia-northeast3", "mission-generation")),
            task.capture(),
        )
        assertEquals("projects/test-project/locations/asia-northeast3/queues/mission-generation/tasks/mission-$jobId-2", taskName)
        assertEquals(taskName, task.value.name)
        assertEquals("https://worker.example.test/internal/mission-generation/jobs/$jobId/execute", task.value.httpRequest.url)
        assertEquals("tasks-invoker@example.test", task.value.httpRequest.oidcToken.serviceAccountEmail)
        assertEquals("https://worker.example.test", task.value.httpRequest.oidcToken.audience)
        assertEquals("2", task.value.httpRequest.headersMap["X-Mission-Generation"])
        assertEquals(300, task.value.dispatchDeadline.seconds)
    }
}

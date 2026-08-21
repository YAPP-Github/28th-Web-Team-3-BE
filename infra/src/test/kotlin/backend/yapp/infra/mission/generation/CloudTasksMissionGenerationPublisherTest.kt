package backend.yapp.infra.mission.generation

import com.google.api.gax.rpc.ApiCallContext
import com.google.api.gax.rpc.UnaryCallable
import com.google.cloud.tasks.v2.CloudTasksClient
import com.google.cloud.tasks.v2.CreateTaskRequest
import com.google.cloud.tasks.v2.QueueName
import com.google.cloud.tasks.v2.Task
import java.util.UUID
import java.time.Duration
import kotlin.test.Test
import kotlin.test.assertEquals
import org.mockito.ArgumentCaptor
import org.mockito.Mockito.mock
import org.mockito.Mockito.verify
import org.mockito.Mockito.`when`

class CloudTasksMissionGenerationPublisherTest {
    @Test
    fun `publishes a deterministically named OIDC task to the private worker`() {
        val client = mock(CloudTasksClient::class.java)
        @Suppress("UNCHECKED_CAST")
        val callable = mock(UnaryCallable::class.java) as UnaryCallable<CreateTaskRequest, Task>
        `when`(client.createTaskCallable()).thenReturn(callable)
        `when`(
            callable.call(
                org.mockito.ArgumentMatchers.any(CreateTaskRequest::class.java),
                org.mockito.ArgumentMatchers.any(ApiCallContext::class.java),
            ),
        ).thenReturn(Task.getDefaultInstance())
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

        val request = ArgumentCaptor.forClass(CreateTaskRequest::class.java)
        val context = ArgumentCaptor.forClass(ApiCallContext::class.java)
        verify(callable).call(request.capture(), context.capture())
        val task = request.value.task
        assertEquals("projects/test-project/locations/asia-northeast3/queues/mission-generation/tasks/mission-$jobId-2", taskName)
        assertEquals(QueueName.of("test-project", "asia-northeast3", "mission-generation").toString(), request.value.parent)
        assertEquals(taskName, task.name)
        assertEquals("https://worker.example.test/internal/mission-generation/jobs/$jobId/execute", task.httpRequest.url)
        assertEquals("tasks-invoker@example.test", task.httpRequest.oidcToken.serviceAccountEmail)
        assertEquals("https://worker.example.test", task.httpRequest.oidcToken.audience)
        assertEquals("2", task.httpRequest.headersMap["X-Mission-Generation"])
        assertEquals(300, task.dispatchDeadline.seconds)
        assertEquals(Duration.ofMillis(500), context.value.timeoutDuration)
    }
}

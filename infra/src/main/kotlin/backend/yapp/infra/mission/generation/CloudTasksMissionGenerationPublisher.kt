package backend.yapp.infra.mission.generation

import backend.yapp.core.mission.generation.port.MissionGenerationTaskPublisher
import com.google.api.gax.rpc.AlreadyExistsException
import com.google.api.gax.grpc.GrpcCallContext
import com.google.cloud.tasks.v2.CloudTasksClient
import com.google.cloud.tasks.v2.CreateTaskRequest
import com.google.cloud.tasks.v2.HttpMethod
import com.google.cloud.tasks.v2.HttpRequest
import com.google.cloud.tasks.v2.OidcToken
import com.google.cloud.tasks.v2.QueueName
import com.google.cloud.tasks.v2.Task
import com.google.protobuf.Duration
import java.time.Duration as JavaDuration
import java.util.UUID
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration

@Configuration
class MissionGenerationDeliveryConfig {
    @Bean
    @ConditionalOnProperty(prefix = "mission.generation.delivery", name = ["enabled"], havingValue = "true")
    fun cloudTasksClient(): CloudTasksClient = CloudTasksClient.create()

    @Bean
    @ConditionalOnProperty(prefix = "mission.generation.delivery", name = ["enabled"], havingValue = "true")
    fun cloudTasksMissionGenerationPublisher(
        client: CloudTasksClient,
        properties: MissionGenerationProperties,
    ): MissionGenerationTaskPublisher = CloudTasksMissionGenerationPublisher(
        client,
        properties.delivery,
        properties.immediateDelivery.publishDeadline,
    )

    @Bean
    @ConditionalOnProperty(
        prefix = "mission.generation.delivery",
        name = ["enabled"],
        havingValue = "false",
        matchIfMissing = true,
    )
    fun disabledMissionGenerationTaskPublisher(): MissionGenerationTaskPublisher =
        MissionGenerationTaskPublisher { _, _ -> error("Mission generation delivery is disabled") }
}

class CloudTasksMissionGenerationPublisher(
    private val client: CloudTasksClient,
    private val properties: DeliveryProperties,
    private val publishDeadline: JavaDuration = JavaDuration.ofMillis(500),
) : MissionGenerationTaskPublisher {
    init {
        require(properties.projectId.isNotBlank()) { "Cloud Tasks project id is required" }
        require(properties.workerUrl.startsWith("https://")) { "Mission worker HTTPS URL is required" }
        require(properties.oidcServiceAccount.isNotBlank()) { "Cloud Tasks OIDC service account is required" }
    }

    override fun publish(jobId: UUID, generation: Int): String {
        val queueName = QueueName.of(properties.projectId, properties.location, properties.queue)
        val taskId = "mission-$jobId-$generation"
        val taskName = "${queueName}/tasks/$taskId"
        val request = HttpRequest.newBuilder()
            .setHttpMethod(HttpMethod.POST)
            .setUrl("${properties.workerUrl.trimEnd('/')}/internal/mission-generation/jobs/$jobId/execute")
            .putHeaders("X-Mission-Generation", generation.toString())
            .setOidcToken(
                OidcToken.newBuilder()
                    .setServiceAccountEmail(properties.oidcServiceAccount)
                    .setAudience(properties.workerUrl.trimEnd('/')),
            )
            .build()
        val task = Task.newBuilder()
            .setName(taskName)
            .setHttpRequest(request)
            .setDispatchDeadline(Duration.newBuilder().setSeconds(90))
            .build()
        try {
            client.createTaskCallable().call(
                CreateTaskRequest.newBuilder().setParent(queueName.toString()).setTask(task).build(),
                GrpcCallContext.createDefault().withTimeoutDuration(publishDeadline),
            )
        } catch (_: AlreadyExistsException) {
            // A prior publish succeeded but its outbox acknowledgement was lost.
        }
        return taskName
    }
}

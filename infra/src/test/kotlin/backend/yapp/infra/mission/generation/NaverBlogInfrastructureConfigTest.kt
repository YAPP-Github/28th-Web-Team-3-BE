package backend.yapp.infra.mission.generation

import backend.yapp.core.mission.generation.port.MissionBlogSearchPort
import io.micrometer.core.instrument.MeterRegistry
import io.micrometer.core.instrument.simple.SimpleMeterRegistry
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNull
import org.springframework.boot.test.context.runner.ApplicationContextRunner
import tools.jackson.databind.ObjectMapper

class NaverBlogInfrastructureConfigTest {
    private val contextRunner = ApplicationContextRunner()
        .withUserConfiguration(NaverBlogInfrastructureConfig::class.java)
        .withBean(MeterRegistry::class.java, { SimpleMeterRegistry() })
        .withBean(ObjectMapper::class.java, { ObjectMapper() })

    @Test
    fun `binds Naver properties and exposes the search adapter through its port`() {
        contextRunner
            .withPropertyValues(
                "mission.generation.naver-blog.base-url=https://naver.example.test",
                "mission.generation.naver-blog.client-id=configured-client-id",
                "mission.generation.naver-blog.client-secret=configured-client-secret",
                "mission.generation.naver-blog.ai-context-count=7",
                "mission.generation.naver-blog.max-attempts=3",
            )
            .run { context ->
                assertNull(context.startupFailure)
                assertIs<NaverBlogSearchAdapter>(context.getBean(MissionBlogSearchPort::class.java))
                val properties = context.getBean(NaverBlogSearchProperties::class.java)
                assertEquals("https://naver.example.test", properties.baseUrl)
                assertEquals("configured-client-id", properties.clientId)
                assertEquals("configured-client-secret", properties.clientSecret)
                assertEquals(7, properties.aiContextCount)
                assertEquals(3, properties.maxAttempts)
            }
    }

    @Test
    fun `keeps credentials blank when no runtime values are configured`() {
        contextRunner.run { context ->
            assertNull(context.startupFailure)
            val properties = context.getBean(NaverBlogSearchProperties::class.java)
            assertEquals("", properties.clientId)
            assertEquals("", properties.clientSecret)
        }
    }
}

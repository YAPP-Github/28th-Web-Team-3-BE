package backend.yapp.infra.mission.generation

import jakarta.validation.constraints.Max
import jakarta.validation.constraints.Min
import org.springframework.boot.context.properties.ConfigurationProperties
import org.springframework.validation.annotation.Validated

@Validated
@ConfigurationProperties("mission.generation.naver-blog")
data class NaverBlogSearchProperties(
    val baseUrl: String = "https://naverapihub.apigw.ntruss.com",
    val clientId: String = "",
    val clientSecret: String = "",
    @field:Min(1) @field:Max(100)
    val aiContextCount: Int = 15,
    @field:Min(1) @field:Max(3)
    val maxAttempts: Int = 2,
)

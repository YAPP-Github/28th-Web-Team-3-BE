package backend.yapp.infra.auth

import jakarta.validation.constraints.NotBlank
import java.time.Duration
import org.springframework.boot.context.properties.ConfigurationProperties
import org.springframework.validation.annotation.Validated

@Validated
@ConfigurationProperties("jwt")
data class JwtProperties(
    @field:NotBlank val secret: String,
    @field:NotBlank val issuer: String,
    @field:NotBlank val audience: String,
    val accessTokenTtl: Duration,
    val refreshTokenTtl: Duration,
)

package backend.yapp.api.global.config

import io.swagger.v3.oas.models.Components
import io.swagger.v3.oas.models.OpenAPI
import io.swagger.v3.oas.models.info.Info
import io.swagger.v3.oas.models.security.SecurityRequirement
import io.swagger.v3.oas.models.security.SecurityScheme
import io.swagger.v3.oas.models.servers.Server
import org.springframework.beans.factory.annotation.Value
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration

@Configuration
class SwaggerConfig(
    @Value("\${app.server.deployment-url}") private val deploymentUrl: String,
) {

    @Bean
    fun openAPI(): OpenAPI =
        OpenAPI()
            .info(
                Info()
                    .title("Yapp Web Team 3")
                    .description("28th YAPP Web Team 3 API 문서")
                    .version("v1"),
            )
            .servers(
                listOf(
                    Server().url(deploymentUrl).description("배포"),
                    Server().url("http://localhost:8080").description("로컬"),
                ),
            )
            .components(
                Components()
                    .addSecuritySchemes(
                        ACCESS_TOKEN_SCHEME_NAME,
                        SecurityScheme()
                            .type(SecurityScheme.Type.HTTP)
                            .scheme("bearer")
                            .bearerFormat("JWT")
                            .`in`(SecurityScheme.In.HEADER)
                            .name("Authorization"),
                    )
                    .addSecuritySchemes(
                        REFRESH_TOKEN_SCHEME_NAME,
                        SecurityScheme()
                            .type(SecurityScheme.Type.APIKEY)
                            .`in`(SecurityScheme.In.COOKIE)
                            .name(REFRESH_TOKEN_COOKIE_NAME),
                    ),
            )
            .addSecurityItem(SecurityRequirement().addList(ACCESS_TOKEN_SCHEME_NAME))
            .addSecurityItem(SecurityRequirement().addList(REFRESH_TOKEN_SCHEME_NAME))

    companion object {
        private const val ACCESS_TOKEN_SCHEME_NAME = "accessTokenAuth"
        private const val REFRESH_TOKEN_SCHEME_NAME = "refreshTokenAuth"
        private const val REFRESH_TOKEN_COOKIE_NAME = "refreshToken"
    }
}

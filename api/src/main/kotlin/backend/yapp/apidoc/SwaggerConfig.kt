package backend.yapp.apidoc

import io.swagger.v3.oas.models.OpenAPI
import io.swagger.v3.oas.models.info.Info
import io.swagger.v3.oas.models.tags.Tag
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration

@Configuration
class SwaggerConfig {
    @Bean
    fun openAPI(): OpenAPI =
        OpenAPI()
            .info(
                Info()
                    .title("YAPP 게스트 인증 API")
                    .version("v1")
                    .description("RN이 보낸 UUID 식별값으로 게스트 계정을 매핑하고 토큰을 발급·재발급합니다."),
            )
            .tags(listOf(Tag().name("Guest Authentication").description("게스트 식별값 및 토큰 관리")))
}

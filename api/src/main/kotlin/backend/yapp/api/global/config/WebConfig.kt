package backend.yapp.api.global.config

import org.springframework.beans.factory.annotation.Value
import org.springframework.context.annotation.Configuration
import org.springframework.web.servlet.config.annotation.CorsRegistry
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer

@Configuration
class WebConfig(
    @Value("\${app.server.deployment-url}") private val deploymentUrl: String,
) : WebMvcConfigurer {

    override fun addCorsMappings(registry: CorsRegistry) {
        registry.addMapping("/**")
            .allowedOriginPatterns(
                "https://localhost:3000",
                "http://localhost:3000",
                "http://localhost:8080",
                deploymentUrl,
                "https://28th-web-team-3-fe-web-dev.vercel.app",
                "https://28th-web-team-3-fe-web.vercel.app",
            )
            .allowedMethods("GET", "POST", "PUT", "PATCH", "DELETE", "OPTIONS")
            .allowedHeaders("*")
            .allowCredentials(true)
    }
}

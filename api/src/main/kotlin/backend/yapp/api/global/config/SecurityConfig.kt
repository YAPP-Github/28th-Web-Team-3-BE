package backend.yapp.api.global.config

import backend.yapp.api.global.exception.ErrorResponseEntity
import backend.yapp.common.exception.BaseException
import backend.yapp.common.exception.ErrorCode
import backend.yapp.core.auth.service.GuestAuthService
import tools.jackson.databind.ObjectMapper
import jakarta.servlet.FilterChain
import jakarta.servlet.http.HttpServletRequest
import jakarta.servlet.http.HttpServletResponse
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.http.HttpMethod
import org.springframework.http.MediaType
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken
import org.springframework.security.config.Customizer.withDefaults
import org.springframework.security.config.annotation.web.builders.HttpSecurity
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity
import org.springframework.security.config.http.SessionCreationPolicy
import org.springframework.security.core.context.SecurityContextHolder
import org.springframework.security.web.SecurityFilterChain
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter
import org.springframework.web.filter.OncePerRequestFilter
import org.springframework.beans.factory.annotation.Value

@Configuration
@EnableWebSecurity
class SecurityConfig(
    private val guestAuthService: GuestAuthService,
    private val objectMapper: ObjectMapper,
    @Value("\${app.role:api}") private val appRole: String,
) {
    @Bean
    fun securityFilterChain(http: HttpSecurity): SecurityFilterChain {
        val entryPoint = { _: HttpServletRequest, response: HttpServletResponse, _: org.springframework.security.core.AuthenticationException ->
            writeErrorResponse(response, objectMapper, ErrorCode.UNAUTHORIZED)
        }
        http
            .cors(withDefaults())
            .csrf { it.disable() }
            .sessionManagement { it.sessionCreationPolicy(SessionCreationPolicy.STATELESS) }
            .exceptionHandling { it.authenticationEntryPoint(entryPoint) }
            .authorizeHttpRequests { authorization ->
                when (appRole) {
                    "mission-worker" -> authorization
                        .requestMatchers(HttpMethod.POST, "/internal/mission-generation/jobs/*/execute").permitAll()
                        .anyRequest().denyAll()
                    "mission-dispatcher" -> authorization
                        .requestMatchers(HttpMethod.POST, "/internal/mission-generation/dispatch").permitAll()
                        .anyRequest().denyAll()
                    else -> authorization
                        .requestMatchers("/internal/**").denyAll()
                        .requestMatchers(
                            HttpMethod.POST,
                            "/api/auth/guest",
                            "/api/auth/guest/refresh",
                        ).permitAll()
                        .requestMatchers("/api/health", "/v3/api-docs/**", "/swagger-ui/**").permitAll()
                        .anyRequest().authenticated()
                }
            }
            .addFilterBefore(BearerTokenFilter(guestAuthService, objectMapper, appRole), UsernamePasswordAuthenticationFilter::class.java)
        return http.build()
    }

}

private class BearerTokenFilter(
    private val guestAuthService: GuestAuthService,
    private val objectMapper: ObjectMapper,
    private val appRole: String,
) : OncePerRequestFilter() {
    override fun shouldNotFilter(request: HttpServletRequest): Boolean =
        appRole != "api" || request.requestURI.startsWith("/internal/") ||
            request.method == org.springframework.http.HttpMethod.POST.name() &&
            request.requestURI in setOf("/api/auth/guest", "/api/auth/guest/refresh")

    override fun doFilterInternal(request: HttpServletRequest, response: HttpServletResponse, filterChain: FilterChain) {
        val authorization = request.getHeader("Authorization")
        if (authorization?.startsWith("Bearer ") == true) {
            try {
                val guestUserId = guestAuthService.authenticate(authorization.removePrefix("Bearer "))
                SecurityContextHolder.getContext().authentication =
                    UsernamePasswordAuthenticationToken(guestUserId, null, emptyList())
            } catch (ex: BaseException) {
                writeErrorResponse(response, objectMapper, ex.errorCode)
                return
            }
        }
        filterChain.doFilter(request, response)
    }
}

private fun writeErrorResponse(response: HttpServletResponse, objectMapper: ObjectMapper, errorCode: ErrorCode) {
    response.status = errorCode.httpStatus.value()
    response.contentType = MediaType.APPLICATION_JSON_VALUE
    objectMapper.writeValue(
        response.outputStream,
        ErrorResponseEntity(errorCode.code, errorCode.name, errorCode.message),
    )
}

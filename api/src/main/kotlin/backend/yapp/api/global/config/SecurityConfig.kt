package backend.yapp.api.global.config

import backend.yapp.api.global.exception.ErrorResponseEntity
import backend.yapp.common.exception.BaseException
import backend.yapp.common.exception.ErrorCode
import backend.yapp.core.auth.service.GuestAuthService
import jakarta.servlet.FilterChain
import jakarta.servlet.http.HttpServletRequest
import jakarta.servlet.http.HttpServletResponse
import org.slf4j.MDC
import org.springframework.beans.factory.annotation.Value
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
import tools.jackson.databind.ObjectMapper

@Configuration
@EnableWebSecurity
class SecurityConfig(
    private val guestAuthService: GuestAuthService,
    private val objectMapper: ObjectMapper,
    @Value("\${app.role:api}") private val appRole: String,
) {
    init {
        require(appRole in SUPPORTED_APP_ROLES) { "Unsupported app.role: $appRole" }
    }

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
                    "policy-sync" -> authorization
                        .requestMatchers(HttpMethod.POST, "/internal/policies/sync").permitAll()
                        .anyRequest().denyAll()
                    "api" -> authorization
                        .requestMatchers("/internal/**").denyAll()
                        .requestMatchers(
                            HttpMethod.POST,
                            "/api/auth/guest",
                            "/api/auth/guest/refresh",
                        ).permitAll()
                        // 관리자 토큰(X-Admin-Token)으로 컨트롤러에서 검증하는 수동 업로드 엔드포인트
                        .requestMatchers(HttpMethod.POST, "/api/admin/policies/import").permitAll()
                        .requestMatchers("/api/health", "/v3/api-docs/**", "/swagger-ui/**").permitAll()
                        .anyRequest().authenticated()
                    else -> error("Unsupported app.role: $appRole")
                }
            }
            .addFilterBefore(BearerTokenFilter(guestAuthService, objectMapper, appRole), UsernamePasswordAuthenticationFilter::class.java)
        return http.build()
    }

    companion object {
        private val SUPPORTED_APP_ROLES = setOf("api", "mission-worker", "mission-dispatcher", "policy-sync")
    }
}

internal class BearerTokenFilter(
    private val guestAuthService: GuestAuthService,
    private val objectMapper: ObjectMapper,
    private val appRole: String,
) : OncePerRequestFilter() {
    override fun shouldNotFilter(request: HttpServletRequest): Boolean =
        appRole != "api" || request.requestURI.startsWith("/internal/") ||
            (
                request.method == HttpMethod.POST.name() &&
                    request.requestURI in setOf("/api/auth/guest", "/api/auth/guest/refresh")
            )

    override fun doFilterInternal(request: HttpServletRequest, response: HttpServletResponse, filterChain: FilterChain) {
        val authorization = request.getHeader("Authorization")
        if (authorization?.startsWith("Bearer ") == true) {
            val guestUserId = try {
                guestAuthService.authenticate(authorization.removePrefix("Bearer "))
            } catch (ex: BaseException) {
                writeErrorResponse(response, objectMapper, ex.errorCode)
                return
            }
            SecurityContextHolder.getContext().authentication =
                UsernamePasswordAuthenticationToken(guestUserId, null, emptyList())
            withGuestUserMdc(guestUserId) {
                filterChain.doFilter(request, response)
            }
            return
        }
        filterChain.doFilter(request, response)
    }

    private fun withGuestUserMdc(guestUserId: Long, action: () -> Unit) {
        MDC.put(GUEST_USER_ID_MDC_KEY, guestUserId.toString())
        try {
            action()
        } finally {
            MDC.remove(GUEST_USER_ID_MDC_KEY)
        }
    }

    companion object {
        const val GUEST_USER_ID_MDC_KEY = "guest_user_id"
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

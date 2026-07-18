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
import org.springframework.http.MediaType
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken
import org.springframework.security.config.annotation.web.builders.HttpSecurity
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity
import org.springframework.security.config.http.SessionCreationPolicy
import org.springframework.security.core.context.SecurityContextHolder
import org.springframework.security.web.SecurityFilterChain
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter
import org.springframework.web.filter.OncePerRequestFilter

@Configuration
@EnableWebSecurity
class SecurityConfig(
    private val guestAuthService: GuestAuthService,
    private val objectMapper: ObjectMapper,
) {
    @Bean
    fun securityFilterChain(http: HttpSecurity): SecurityFilterChain {
        val entryPoint = { _: HttpServletRequest, response: HttpServletResponse, _: org.springframework.security.core.AuthenticationException ->
            writeErrorResponse(response, objectMapper, ErrorCode.UNAUTHORIZED)
        }
        http
            .csrf { it.disable() }
            .sessionManagement { it.sessionCreationPolicy(SessionCreationPolicy.STATELESS) }
            .exceptionHandling { it.authenticationEntryPoint(entryPoint) }
            .authorizeHttpRequests {
                it.requestMatchers("/api/auth/**", "/api/health", "/v3/api-docs/**", "/swagger-ui/**").permitAll()
                    .anyRequest().authenticated()
            }
            .addFilterBefore(BearerTokenFilter(guestAuthService, objectMapper), UsernamePasswordAuthenticationFilter::class.java)
        return http.build()
    }

}

private class BearerTokenFilter(
    private val guestAuthService: GuestAuthService,
    private val objectMapper: ObjectMapper,
) : OncePerRequestFilter() {
    override fun shouldNotFilter(request: HttpServletRequest): Boolean =
        request.requestURI.startsWith("/api/auth/")

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

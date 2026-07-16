package backend.yapp.api.auth.controller

import backend.yapp.api.auth.dto.GuestAuthRequest
import backend.yapp.api.auth.dto.RefreshTokenRequest
import backend.yapp.api.auth.dto.TokenResponse
import backend.yapp.apidoc.auth.GuestAuthApi
import backend.yapp.common.exception.BaseException
import backend.yapp.common.exception.ErrorCode
import backend.yapp.core.auth.service.GuestAuthService
import java.util.UUID
import jakarta.validation.Valid
import org.springframework.http.HttpStatus
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.ResponseStatus
import org.springframework.web.bind.annotation.RestController

@RestController
@RequestMapping("/api/auth/guest")
class GuestAuthController(private val guestAuthService: GuestAuthService) : GuestAuthApi {
    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    override fun issue(@Valid @RequestBody request: GuestAuthRequest): TokenResponse {
        validateUuid(request.uuid)
        return TokenResponse.from(guestAuthService.issueForIdentifier(request.uuid))
    }

    @PostMapping("/refresh")
    override fun refresh(@Valid @RequestBody request: RefreshTokenRequest): TokenResponse =
        TokenResponse.from(guestAuthService.rotate(request.refreshToken))

    private fun validateUuid(value: String) {
        runCatching { UUID.fromString(value) }
            .onFailure { throw BaseException(ErrorCode.INVALID_IDENTIFIER) }
    }
}

package backend.yapp.apidoc.auth

import backend.yapp.api.auth.dto.GuestAuthRequest
import backend.yapp.api.auth.dto.RefreshTokenRequest
import backend.yapp.api.auth.dto.TokenResponse
import backend.yapp.api.global.exception.ErrorResponseEntity
import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.media.Content
import io.swagger.v3.oas.annotations.media.ExampleObject
import io.swagger.v3.oas.annotations.media.Schema
import io.swagger.v3.oas.annotations.responses.ApiResponse
import io.swagger.v3.oas.annotations.responses.ApiResponses
import io.swagger.v3.oas.annotations.tags.Tag

@Tag(name = "Guest Authentication", description = "UUID 식별값 기반의 게스트 사용자 인증 API")
interface GuestAuthApi {
    @Operation(
        summary = "게스트 토큰 발급",
        description = "클라이언트 UUID 식별값으로 게스트 사용자를 생성하거나 기존 사용자에 매핑한 뒤 Access Token과 Refresh Token을 반환합니다.",
        requestBody = io.swagger.v3.oas.annotations.parameters.RequestBody(
            required = true,
            content = [Content(
                schema = Schema(implementation = GuestAuthRequest::class),
                examples = [ExampleObject(value = "{\"uuid\":\"b7a0df28-2131-4ec1-9679-cb6bd2d95c3f\"}")],
            )],
        ),
    )
    @ApiResponses(
        ApiResponse(responseCode = "201", description = "토큰 발급 성공", content = [Content(
            schema = Schema(implementation = TokenResponse::class),
            examples = [ExampleObject(value = "{\"accessToken\":\"<access-token>\",\"refreshToken\":\"<refresh-token>\"}")],
        )]),
        ApiResponse(responseCode = "400", description = "INVALID_IDENTIFIER 또는 VALIDATION_FAILED", content = [Content(schema = Schema(implementation = ErrorResponseEntity::class))]),
        ApiResponse(responseCode = "500", description = "INTERNAL_SERVER_ERROR", content = [Content(schema = Schema(implementation = ErrorResponseEntity::class))]),
    )
    fun issue(request: GuestAuthRequest): TokenResponse

    @Operation(
        summary = "게스트 토큰 재발급",
        description = "유효한 Refresh Token을 한 번 소비하고 새 Access Token과 Refresh Token을 반환합니다. 기존 Refresh Token은 재사용할 수 없습니다.",
        requestBody = io.swagger.v3.oas.annotations.parameters.RequestBody(
            required = true,
            content = [Content(
                schema = Schema(implementation = RefreshTokenRequest::class),
                examples = [ExampleObject(value = "{\"refreshToken\":\"<refresh-token>\"}")],
            )],
        ),
    )
    @ApiResponses(
        ApiResponse(responseCode = "200", description = "토큰 재발급 성공", content = [Content(schema = Schema(implementation = TokenResponse::class))]),
        ApiResponse(responseCode = "400", description = "VALIDATION_FAILED", content = [Content(schema = Schema(implementation = ErrorResponseEntity::class))]),
        ApiResponse(responseCode = "401", description = "UNAUTHORIZED: 만료·위조·재사용·잘못된 유형의 Refresh Token", content = [Content(schema = Schema(implementation = ErrorResponseEntity::class))]),
        ApiResponse(responseCode = "500", description = "INTERNAL_SERVER_ERROR", content = [Content(schema = Schema(implementation = ErrorResponseEntity::class))]),
    )
    fun refresh(request: RefreshTokenRequest): TokenResponse
}

package backend.yapp.apidoc.auth

import backend.yapp.api.auth.dto.CurrentUserResponse
import backend.yapp.api.global.exception.ErrorResponseEntity
import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.media.Content
import io.swagger.v3.oas.annotations.media.ExampleObject
import io.swagger.v3.oas.annotations.media.Schema
import io.swagger.v3.oas.annotations.responses.ApiResponse
import io.swagger.v3.oas.annotations.responses.ApiResponses
import io.swagger.v3.oas.annotations.security.SecurityRequirement
import io.swagger.v3.oas.annotations.tags.Tag

@Tag(name = "Current User", description = "현재 인증된 사용자 조회 API")
interface CurrentUserApi {
    @Operation(
        summary = "현재 사용자와 온보딩 완료 여부 조회",
        description = "Access Token으로 사용자를 검증하고 사용자 ID와 온보딩 완료 여부를 반환합니다.",
        security = [SecurityRequirement(name = "accessTokenAuth")],
    )
    @ApiResponses(
        ApiResponse(responseCode = "200", description = "조회 성공", content = [Content(
            schema = Schema(implementation = CurrentUserResponse::class),
            examples = [ExampleObject(value = "{\"userId\":1,\"onboardingCompleted\":false}")],
        )]),
        ApiResponse(responseCode = "401", description = "UNAUTHORIZED: Access Token이 없거나 유효하지 않음", content = [Content(schema = Schema(implementation = ErrorResponseEntity::class))]),
    )
    fun getCurrentUser(userId: Long): CurrentUserResponse
}

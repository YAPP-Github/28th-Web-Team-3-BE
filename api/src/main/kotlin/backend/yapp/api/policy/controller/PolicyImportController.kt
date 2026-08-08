package backend.yapp.api.policy.controller

import backend.yapp.api.policy.dto.PolicySyncResponse
import backend.yapp.common.exception.BaseException
import backend.yapp.common.exception.ErrorCode
import backend.yapp.core.policy.service.PolicySyncService
import backend.yapp.infra.policy.YouthPolicyResponseParser
import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.tags.Tag
import org.springframework.beans.factory.annotation.Value
import org.springframework.http.MediaType
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestHeader
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestPart
import org.springframework.web.bind.annotation.RestController
import org.springframework.web.multipart.MultipartFile

/**
 * 온통청년 청년정책 데이터 수동 갱신. 클라우드 IP가 온통청년 API에 차단되므로,
 * 사람이 한국 IP에서 받아온 응답 JSON을 업로드하면 파싱해 DB를 upsert 한다.
 * 관리자 토큰(`X-Admin-Token`) 헤더로 보호한다.
 */
@Tag(name = "Policy Admin", description = "청년정책 데이터 수동 업로드(관리자 전용). 온통청년 API 응답 JSON을 업로드하면 파싱·필터·저장.")
@RestController
@RequestMapping("/api/admin/policies")
class PolicyImportController(
    private val responseParser: YouthPolicyResponseParser,
    private val policySyncService: PolicySyncService,
    @Value("\${admin.import-token:}") private val importToken: String,
) {
    @Operation(
        summary = "청년정책 JSON 업로드",
        description = "온통청년 목록조회 API 응답(JSON) 파일을 업로드한다. pageSize를 크게(예: 3000) 호출하면 전체를 한 파일로 받을 수 있다.",
    )
    @PostMapping("/import", consumes = [MediaType.MULTIPART_FORM_DATA_VALUE])
    fun import(
        @RequestHeader("X-Admin-Token") token: String?,
        @RequestPart("file") file: MultipartFile,
    ): PolicySyncResponse {
        verifyToken(token)
        val json = String(file.bytes, Charsets.UTF_8)
        val policies = responseParser.parse(json)
        return PolicySyncResponse.from(policySyncService.ingest(policies))
    }

    private fun verifyToken(token: String?) {
        if (importToken.isBlank() || token != importToken) throw BaseException(ErrorCode.UNAUTHORIZED)
    }
}

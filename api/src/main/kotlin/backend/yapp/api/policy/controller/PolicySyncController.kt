package backend.yapp.api.policy.controller

import backend.yapp.api.policy.dto.PolicySyncResponse
import backend.yapp.core.policy.service.PolicySyncService
import io.swagger.v3.oas.annotations.tags.Tag
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

/**
 * 청년정책 동기화 트리거. Cloud Scheduler가 하루 1회 호출한다(`app.role=policy-sync` 배포에서만 노출).
 */
@Tag(name = "Policy Sync (internal)", description = "청년정책 동기화 (Cloud Scheduler 트리거)")
@RestController
@RequestMapping("/internal/policies")
class PolicySyncController(
    private val policySyncService: PolicySyncService,
) {
    @PostMapping("/sync")
    fun sync(): PolicySyncResponse = PolicySyncResponse.from(policySyncService.sync())
}

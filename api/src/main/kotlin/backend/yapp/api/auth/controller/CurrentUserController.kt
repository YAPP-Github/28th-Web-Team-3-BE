package backend.yapp.api.auth.controller

import backend.yapp.api.auth.dto.CurrentUserResponse
import backend.yapp.apidoc.auth.CurrentUserApi
import backend.yapp.core.onboarding.service.OnboardingCompletionService
import org.springframework.security.core.annotation.AuthenticationPrincipal
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

@RestController
@RequestMapping("/api/auth/me")
class CurrentUserController(
    private val onboardingCompletionService: OnboardingCompletionService,
) : CurrentUserApi {
    @GetMapping
    override fun getCurrentUser(@AuthenticationPrincipal userId: Long): CurrentUserResponse =
        CurrentUserResponse(
            userId = userId,
            onboardingCompleted = onboardingCompletionService.isCompleted(userId),
        )
}

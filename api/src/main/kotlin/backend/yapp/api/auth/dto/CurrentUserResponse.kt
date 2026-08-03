package backend.yapp.api.auth.dto

data class CurrentUserResponse(
    val userId: Long,
    val onboardingCompleted: Boolean,
)

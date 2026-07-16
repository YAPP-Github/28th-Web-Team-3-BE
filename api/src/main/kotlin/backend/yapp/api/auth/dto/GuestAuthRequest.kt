package backend.yapp.api.auth.dto

import jakarta.validation.constraints.NotBlank

data class GuestAuthRequest(@field:NotBlank val uuid: String)

package backend.yapp.api.auth.dto

import backend.yapp.core.auth.port.TokenPair

data class TokenResponse(val accessToken: String, val refreshToken: String) {
    companion object {
        fun from(pair: TokenPair) = TokenResponse(pair.accessToken, pair.refreshToken)
    }
}

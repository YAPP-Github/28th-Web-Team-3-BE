package backend.yapp.api.global.exception

import backend.yapp.common.exception.ErrorCode
import org.springframework.http.ResponseEntity

data class ErrorResponseEntity(
    val code: Int,
    val name: String,
    val message: String,
    val errors: List<String>? = null,
) {
    companion object {
        fun toResponseEntity(errorCode: ErrorCode): ResponseEntity<ErrorResponseEntity> =
            toResponseEntity(errorCode, errorCode.message, null)

        fun toResponseEntity(
            errorCode: ErrorCode,
            errors: List<String>,
        ): ResponseEntity<ErrorResponseEntity> =
            toResponseEntity(errorCode, errorCode.message, errors)

        fun toResponseEntity(
            errorCode: ErrorCode,
            message: String,
            errors: List<String>?,
        ): ResponseEntity<ErrorResponseEntity> =
            ResponseEntity
                .status(errorCode.httpStatus)
                .body(
                    ErrorResponseEntity(
                        code = errorCode.code,
                        name = errorCode.name,
                        message = message,
                        errors = errors,
                    ),
                )
    }
}

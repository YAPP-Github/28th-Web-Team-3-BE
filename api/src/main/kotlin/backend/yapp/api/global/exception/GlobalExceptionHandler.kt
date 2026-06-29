package backend.yapp.api.global.exception

import backend.yapp.common.exception.BaseException
import backend.yapp.common.exception.ErrorCode
import org.slf4j.LoggerFactory
import org.springframework.http.ResponseEntity
import org.springframework.web.HttpMediaTypeNotSupportedException
import org.springframework.web.HttpRequestMethodNotSupportedException
import org.springframework.web.bind.MethodArgumentNotValidException
import org.springframework.web.bind.annotation.ExceptionHandler
import org.springframework.web.bind.annotation.RestControllerAdvice
import org.springframework.web.multipart.support.MissingServletRequestPartException
import org.springframework.web.servlet.NoHandlerFoundException

@RestControllerAdvice
class GlobalExceptionHandler {

    private val log = LoggerFactory.getLogger(GlobalExceptionHandler::class.java)

    @ExceptionHandler(BaseException::class)
    fun handleBaseException(ex: BaseException): ResponseEntity<ErrorResponseEntity> =
        ErrorResponseEntity.toResponseEntity(ex.errorCode)

    @ExceptionHandler(MethodArgumentNotValidException::class)
    fun handleMethodArgumentNotValid(ex: MethodArgumentNotValidException): ResponseEntity<ErrorResponseEntity> {
        val errors = ex.bindingResult.fieldErrors.map { "${it.field}: ${it.defaultMessage}" }
        return ErrorResponseEntity.toResponseEntity(ErrorCode.VALIDATION_FAILED, errors)
    }

    @ExceptionHandler(MissingServletRequestPartException::class)
    fun handleMissingPart(ex: MissingServletRequestPartException): ResponseEntity<ErrorResponseEntity> {
        val errorCode = ErrorCode.MISSING_PART
        val message = "${errorCode.message} (${ex.requestPartName})"
        return ErrorResponseEntity.toResponseEntity(errorCode, message, null)
    }

    @ExceptionHandler(NoHandlerFoundException::class)
    fun handleNoHandlerFound(ex: NoHandlerFoundException): ResponseEntity<ErrorResponseEntity> {
        val errorCode = ErrorCode.NO_HANDLER_FOUND
        val message = "${errorCode.message} (${ex.requestURL})"
        return ErrorResponseEntity.toResponseEntity(errorCode, message, null)
    }

    @ExceptionHandler(HttpRequestMethodNotSupportedException::class)
    fun handleMethodNotSupported(ex: HttpRequestMethodNotSupportedException): ResponseEntity<ErrorResponseEntity> {
        val errorCode = ErrorCode.METHOD_NOT_ALLOWED
        val message = "${errorCode.message} (${ex.method})"
        return ErrorResponseEntity.toResponseEntity(errorCode, message, null)
    }

    @ExceptionHandler(HttpMediaTypeNotSupportedException::class)
    fun handleHttpMediaTypeNotSupported(ex: HttpMediaTypeNotSupportedException): ResponseEntity<ErrorResponseEntity> {
        val errorCode = ErrorCode.UNSUPPORTED_MEDIA_TYPE
        val message = "${errorCode.message} (${ex.contentType})"
        return ErrorResponseEntity.toResponseEntity(errorCode, message, null)
    }

    @ExceptionHandler(Exception::class)
    fun handleException(ex: Exception): ResponseEntity<ErrorResponseEntity> {
        log.error("예상치 못한 예외", ex)
        return ErrorResponseEntity.toResponseEntity(ErrorCode.INTERNAL_SERVER_ERROR)
    }
}

package backend.yapp.common.exception

class BaseException(
    val errorCode: ErrorCode,
) : RuntimeException(errorCode.message)

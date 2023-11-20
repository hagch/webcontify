package io.webcontify.backend.collections.exceptions

import io.webcontify.backend.collections.models.apis.ErrorCode

abstract class BaseException(val code: ErrorCode, val messageInclusions: List<String?>?) :
    RuntimeException()

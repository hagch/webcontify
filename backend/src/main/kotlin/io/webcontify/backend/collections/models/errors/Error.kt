package io.webcontify.backend.collections.models.errors

import io.webcontify.backend.collections.exceptions.BaseException

data class Error(val code: ErrorCode, val message: String) {
  constructor(code: ErrorCode) : this(code, code.message)

  constructor(
      stringCode: String
  ) : this(ErrorCode.valueOf(stringCode), ErrorCode.valueOf(stringCode).message)

  constructor(exception: BaseException) : this(exception.code, exception.code.message)
}

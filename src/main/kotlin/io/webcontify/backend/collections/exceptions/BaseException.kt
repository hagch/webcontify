package io.webcontify.backend.collections.exceptions

import io.webcontify.backend.collections.models.errors.Error
import io.webcontify.backend.collections.models.errors.ErrorCode

abstract class BaseException(val code: ErrorCode, private vararg val messageInclusions: String) :
    RuntimeException() {

  private fun toError(): Error {
    return messageInclusions.let {
      Error(code, if (it.isEmpty()) code.message else String.format(code.message, *it))
    }
  }

  fun toErrors(): List<Error> {
    return listOf(toError())
  }
}

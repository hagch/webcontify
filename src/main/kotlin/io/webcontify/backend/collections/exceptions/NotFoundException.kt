package io.webcontify.backend.collections.exceptions

import io.webcontify.backend.collections.models.errors.ErrorCode

class NotFoundException(code: ErrorCode, vararg messageInclusions: String) :
    BaseException(code, *messageInclusions) {
  constructor(code: ErrorCode) : this(code, *emptyArray())
}

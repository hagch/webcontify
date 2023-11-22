package io.webcontify.backend.collections.exceptions

import io.webcontify.backend.collections.models.apis.ErrorCode

class UnprocessableContentException(code: ErrorCode, vararg messageInclusions: String) :
    BaseException(code, *messageInclusions) {
  constructor(code: ErrorCode) : this(code, *emptyArray())
}

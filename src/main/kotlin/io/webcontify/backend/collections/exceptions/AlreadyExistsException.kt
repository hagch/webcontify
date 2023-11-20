package io.webcontify.backend.collections.exceptions

import io.webcontify.backend.collections.models.apis.ErrorCode

class AlreadyExistsException(code: ErrorCode, messageInclusions: List<String?>?) :
    BaseException(code, messageInclusions) {
  constructor(code: ErrorCode) : this(code, null)

  constructor(code: ErrorCode, messageInclusion: String) : this(code, listOf(messageInclusion))
}

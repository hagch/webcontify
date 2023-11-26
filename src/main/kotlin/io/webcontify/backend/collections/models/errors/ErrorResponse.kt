package io.webcontify.backend.collections.models.errors

import io.webcontify.backend.collections.exceptions.BaseException
import java.time.LocalDateTime

data class ErrorResponse(val instance: String, val errors: List<Error>) {
  val timestamp: LocalDateTime = LocalDateTime.now()

  constructor(instance: String, exception: BaseException) : this(instance, listOf(Error(exception)))

  constructor(instance: String, code: ErrorCode) : this(instance, listOf(Error(code)))
}

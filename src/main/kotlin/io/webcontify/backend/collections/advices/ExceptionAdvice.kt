package io.webcontify.backend.collections.advices

import io.webcontify.backend.collections.exceptions.AlreadyExistsException
import io.webcontify.backend.collections.exceptions.NotFoundException
import io.webcontify.backend.collections.exceptions.UnprocessableContentException
import io.webcontify.backend.collections.models.apis.Error
import io.webcontify.backend.collections.models.apis.ErrorCode
import io.webcontify.backend.collections.models.apis.ErrorResponse
import jakarta.validation.ConstraintViolationException
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.ControllerAdvice
import org.springframework.web.bind.annotation.ExceptionHandler
import org.springframework.web.context.request.WebRequest

@ControllerAdvice
class ExceptionAdvice {

  @ExceptionHandler(value = [NotFoundException::class])
  fun handleNotFound(
      exception: NotFoundException,
      request: WebRequest
  ): ResponseEntity<ErrorResponse> {
    val error: Error =
        exception.messageInclusions?.let {
          Error(exception.code, String.format(exception.code.message, it))
        }
            ?: Error(exception.code)
    return ResponseEntity(ErrorResponse(request.contextPath, listOf(error)), HttpStatus.NOT_FOUND)
  }

  @ExceptionHandler(value = [AlreadyExistsException::class])
  fun handleAlreadyExists(
      exception: AlreadyExistsException,
      request: WebRequest
  ): ResponseEntity<ErrorResponse> {
    val error: Error =
        exception.messageInclusions?.let {
          Error(exception.code, String.format(exception.code.message, it))
        }
            ?: Error(exception.code)
    return ResponseEntity(ErrorResponse(request.contextPath, listOf(error)), HttpStatus.CONFLICT)
  }

  @ExceptionHandler(value = [UnprocessableContentException::class])
  fun handleUnprocessableContent(
      exception: UnprocessableContentException,
      request: WebRequest
  ): ResponseEntity<ErrorResponse> {
    val error: Error =
        exception.messageInclusions?.let {
          Error(exception.code, String.format(exception.code.message, it))
        }
            ?: Error(exception.code)
    return ResponseEntity(ErrorResponse(request.contextPath, listOf(error)), HttpStatus.BAD_REQUEST)
  }

  @ExceptionHandler(value = [ConstraintViolationException::class])
  fun handleUnknownBehaviour(
      exception: ConstraintViolationException,
      request: WebRequest
  ): ResponseEntity<ErrorResponse> {
    val errors = exception.constraintViolations.map { Error(it.message) }
    return ResponseEntity(ErrorResponse(request.contextPath, errors), HttpStatus.BAD_REQUEST)
  }

  @ExceptionHandler(value = [Exception::class])
  fun handleUnknownBehaviour(
      exception: Exception,
      request: WebRequest
  ): ResponseEntity<ErrorResponse> {
    return ResponseEntity(
        ErrorResponse(request.contextPath, ErrorCode.INTERNAL_SERVER_ERROR),
        HttpStatus.INTERNAL_SERVER_ERROR)
  }
}

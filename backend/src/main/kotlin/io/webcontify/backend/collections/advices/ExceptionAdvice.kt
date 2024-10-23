package io.webcontify.backend.collections.advices

import io.webcontify.backend.collections.exceptions.AlreadyExistsException
import io.webcontify.backend.collections.exceptions.NotFoundException
import io.webcontify.backend.collections.exceptions.UnprocessableContentException
import io.webcontify.backend.collections.models.errors.Error
import io.webcontify.backend.collections.models.errors.ErrorCode
import io.webcontify.backend.collections.models.errors.ErrorResponse
import jakarta.servlet.http.HttpServletRequest
import jakarta.validation.ConstraintViolationException
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.http.converter.HttpMessageNotReadableException
import org.springframework.web.bind.MethodArgumentNotValidException
import org.springframework.web.bind.annotation.ControllerAdvice
import org.springframework.web.bind.annotation.ExceptionHandler

@ControllerAdvice
class ExceptionAdvice {

  @ExceptionHandler(value = [NotFoundException::class])
  fun handleNotFound(
      exception: NotFoundException,
      request: HttpServletRequest
  ): ResponseEntity<ErrorResponse> {
    return ResponseEntity(
        ErrorResponse(request.requestURI, exception.toErrors()), HttpStatus.NOT_FOUND)
  }

  @ExceptionHandler(value = [AlreadyExistsException::class])
  fun handleAlreadyExists(
      exception: AlreadyExistsException,
      request: HttpServletRequest
  ): ResponseEntity<ErrorResponse> {
    return ResponseEntity(
        ErrorResponse(request.requestURI, exception.toErrors()), HttpStatus.CONFLICT)
  }

  @ExceptionHandler(
      value = [UnprocessableContentException::class, HttpMessageNotReadableException::class])
  fun handleUnprocessableContent(
      exception: Exception,
      request: HttpServletRequest
  ): ResponseEntity<ErrorResponse> {
    if (exception is UnprocessableContentException) {
      return ResponseEntity(
          ErrorResponse(request.requestURI, exception.toErrors()), HttpStatus.BAD_REQUEST)
    }
    exception as HttpMessageNotReadableException
    return ResponseEntity(
        ErrorResponse(
            request.requestURI,
            listOf(
                Error(
                    ErrorCode.INVALID_REQUEST_BODY,
                    exception.message ?: ErrorCode.INVALID_REQUEST_BODY.message))),
        HttpStatus.BAD_REQUEST)
  }

  @ExceptionHandler(value = [ConstraintViolationException::class])
  fun handleConstraintViolationBehaviour(
      exception: ConstraintViolationException,
      request: HttpServletRequest
  ): ResponseEntity<ErrorResponse> {
    val errors = exception.constraintViolations.map { Error(it.message) }
    return ResponseEntity(ErrorResponse(request.requestURI, errors), HttpStatus.BAD_REQUEST)
  }

  @ExceptionHandler(value = [MethodArgumentNotValidException::class])
  fun handleInvalidMethodArgumentBehaviour(
      exception: MethodArgumentNotValidException,
      request: HttpServletRequest
  ): ResponseEntity<ErrorResponse> {
    val errors =
        exception.allErrors.map { Error(ErrorCode.valueOf(it.defaultMessage?.toString() ?: "")) }
    return ResponseEntity(ErrorResponse(request.requestURI, errors), HttpStatus.BAD_REQUEST)
  }
}

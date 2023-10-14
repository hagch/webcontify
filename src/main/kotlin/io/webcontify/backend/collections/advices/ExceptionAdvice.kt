package io.webcontify.backend.collections.advices

import io.webcontify.backend.collections.exceptions.AlreadyExistsException
import io.webcontify.backend.collections.exceptions.NotFoundException
import io.webcontify.backend.collections.exceptions.UnprocessableContentException
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.ControllerAdvice
import org.springframework.web.bind.annotation.ExceptionHandler

@ControllerAdvice
class ExceptionAdvice {

  @ExceptionHandler(value = [NotFoundException::class])
  fun handleNotFound(exception: NotFoundException): ResponseEntity<*> {
    return ResponseEntity(exception.message, HttpStatus.NOT_FOUND)
  }

  @ExceptionHandler(value = [AlreadyExistsException::class])
  fun handleAlreadyExists(exception: AlreadyExistsException): ResponseEntity<*> {
    return ResponseEntity(exception.message, HttpStatus.CONFLICT)
  }

  @ExceptionHandler(value = [UnprocessableContentException::class])
  fun handleUnprocessableContent(exception: UnprocessableContentException): ResponseEntity<*> {
    return ResponseEntity(exception.message, HttpStatus.UNPROCESSABLE_ENTITY)
  }
}

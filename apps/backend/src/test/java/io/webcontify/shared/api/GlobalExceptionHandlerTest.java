package io.webcontify.shared.api;

import static org.assertj.core.api.Assertions.assertThat;

import io.webcontify.shared.exception.*;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;

class GlobalExceptionHandlerTest {

    private final GlobalExceptionHandler handler = new GlobalExceptionHandler();

    @Test
    void shouldHandleNotFoundException() {
        var exception = new NotFoundException("tenant.not_found");

        ProblemDetail result = handler.handleNotFoundException(exception);

        assertThat(result.getStatus()).isEqualTo(HttpStatus.NOT_FOUND.value());
        assertThat(result.getTitle()).isEqualTo("Not Found");
        assertThat(result.getProperties()).containsEntry("code", "tenant.not_found");
    }

    @Test
    void shouldHandleConflictException() {
        var exception = new ConflictException("tenant.slug_already_exists");

        ProblemDetail result = handler.handleConflictException(exception);

        assertThat(result.getStatus()).isEqualTo(HttpStatus.CONFLICT.value());
        assertThat(result.getTitle()).isEqualTo("Conflict");
        assertThat(result.getProperties()).containsEntry("code", "tenant.slug_already_exists");
    }

    @Test
    void shouldHandleForbiddenException() {
        var exception = new ForbiddenException("access.denied");

        ProblemDetail result = handler.handleForbiddenException(exception);

        assertThat(result.getStatus()).isEqualTo(HttpStatus.FORBIDDEN.value());
        assertThat(result.getTitle()).isEqualTo("Forbidden");
        assertThat(result.getProperties()).containsEntry("code", "access.denied");
    }

    @Test
    void shouldHandleValidationException() {
        var fieldErrors = List.of(
                new FieldError("name", "validation.required"),
                new FieldError("slug", "validation.invalid_format"));
        var exception = new ValidationException("validation.failed", fieldErrors);

        ProblemDetail result = handler.handleValidationException(exception);

        assertThat(result.getStatus()).isEqualTo(HttpStatus.BAD_REQUEST.value());
        assertThat(result.getTitle()).isEqualTo("Bad Request");
        assertThat(result.getProperties()).containsEntry("code", "validation.failed");
        @SuppressWarnings("unchecked")
        List<FieldError> errors = (List<FieldError>) result.getProperties().get("errors");
        assertThat(errors).hasSize(2);
        assertThat(errors.get(0).field()).isEqualTo("name");
        assertThat(errors.get(0).message()).isEqualTo("validation.required");
    }

    @Test
    void shouldHandleUnexpectedException() {
        var exception = new RuntimeException("something went wrong");

        ProblemDetail result = handler.handleUnexpectedException(exception);

        assertThat(result.getStatus()).isEqualTo(HttpStatus.INTERNAL_SERVER_ERROR.value());
        assertThat(result.getTitle()).isEqualTo("Internal Server Error");
        assertThat(result.getProperties()).containsEntry("code", "internal.error");
    }
}

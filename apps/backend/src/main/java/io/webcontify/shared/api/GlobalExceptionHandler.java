package io.webcontify.shared.api;

import io.webcontify.shared.exception.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice
public class GlobalExceptionHandler {

    private static final Logger LOG = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    @ExceptionHandler(NotFoundException.class)
    public ProblemDetail handleNotFoundException(NotFoundException ex) {
        return createProblemDetail(ex.getStatus(), ex.getCode());
    }

    @ExceptionHandler(ConflictException.class)
    public ProblemDetail handleConflictException(ConflictException ex) {
        return createProblemDetail(ex.getStatus(), ex.getCode());
    }

    @ExceptionHandler(ForbiddenException.class)
    public ProblemDetail handleForbiddenException(ForbiddenException ex) {
        return createProblemDetail(ex.getStatus(), ex.getCode());
    }

    @ExceptionHandler(ValidationException.class)
    public ProblemDetail handleValidationException(ValidationException ex) {
        ProblemDetail detail = createProblemDetail(ex.getStatus(), ex.getCode());
        detail.setProperty("errors", ex.getFieldErrors());
        return detail;
    }

    @ExceptionHandler(Exception.class)
    public ProblemDetail handleUnexpectedException(Exception ex) {
        LOG.error("Unexpected error", ex);
        return createProblemDetail(HttpStatus.INTERNAL_SERVER_ERROR, "internal.error");
    }

    private ProblemDetail createProblemDetail(HttpStatus status, String code) {
        ProblemDetail detail = ProblemDetail.forStatus(status);
        detail.setTitle(status.getReasonPhrase());
        detail.setProperty("code", code);
        return detail;
    }
}

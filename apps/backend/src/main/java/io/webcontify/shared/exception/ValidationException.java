package io.webcontify.shared.exception;

import java.util.List;
import org.springframework.http.HttpStatus;

public class ValidationException extends WebcontifyException {

    private final List<FieldError> fieldErrors;

    public ValidationException(String code, List<FieldError> fieldErrors) {
        super(code, HttpStatus.BAD_REQUEST);
        this.fieldErrors = fieldErrors;
    }

    public List<FieldError> getFieldErrors() {
        return fieldErrors;
    }
}

package io.webcontify.shared.exception;

import org.springframework.http.HttpStatus;

public class ConflictException extends WebcontifyException {

    public ConflictException(String code) {
        super(code, HttpStatus.CONFLICT);
    }
}

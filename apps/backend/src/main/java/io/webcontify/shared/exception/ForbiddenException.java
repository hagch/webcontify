package io.webcontify.shared.exception;

import org.springframework.http.HttpStatus;

public class ForbiddenException extends WebcontifyException {

    public ForbiddenException(String code) {
        super(code, HttpStatus.FORBIDDEN);
    }
}

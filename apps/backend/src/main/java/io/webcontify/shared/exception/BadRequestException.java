package io.webcontify.shared.exception;

import org.springframework.http.HttpStatus;

public class BadRequestException extends WebcontifyException {

    public BadRequestException(String code) {
        super(code, HttpStatus.BAD_REQUEST);
    }
}

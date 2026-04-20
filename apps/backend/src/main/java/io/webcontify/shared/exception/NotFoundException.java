package io.webcontify.shared.exception;

import org.springframework.http.HttpStatus;

public class NotFoundException extends WebcontifyException {

    public NotFoundException(String code) {
        super(code, HttpStatus.NOT_FOUND);
    }
}

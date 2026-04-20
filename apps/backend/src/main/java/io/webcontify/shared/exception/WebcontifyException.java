package io.webcontify.shared.exception;

import org.springframework.http.HttpStatus;

public abstract class WebcontifyException extends RuntimeException {

    private final String code;
    private final HttpStatus status;

    protected WebcontifyException(String code, HttpStatus status) {
        super(code);
        this.code = code;
        this.status = status;
    }

    public String getCode() {
        return code;
    }

    public HttpStatus getStatus() {
        return status;
    }
}

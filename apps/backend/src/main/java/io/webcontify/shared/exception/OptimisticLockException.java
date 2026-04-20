package io.webcontify.shared.exception;

import org.springframework.http.HttpStatus;

public class OptimisticLockException extends WebcontifyException {

    public OptimisticLockException() {
        super("entry.optimistic_lock_failed", HttpStatus.CONFLICT);
    }
}

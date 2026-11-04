package com.financetracker.exceptions;

import java.util.List;

/** Raised when transaction, budget, receipt or query input fails validation. */
public class InvalidTransactionException extends RuntimeException {
    private static final long serialVersionUID = 1L;

    private final transient List<String> errors;

    public InvalidTransactionException(String message) {
        this(message, List.of(message));
    }

    public InvalidTransactionException(String message, List<String> errors) {
        super(message);
        this.errors = errors == null || errors.isEmpty() ? List.of(message) : List.copyOf(errors);
    }

    public List<String> errors() {
        return errors;
    }
}

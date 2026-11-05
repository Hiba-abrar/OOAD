package com.obfds.exception;

/** Base type for all domain errors raised by the banking layer. */
public class BankingException extends RuntimeException {
    public BankingException(String message) {
        super(message);
    }
}

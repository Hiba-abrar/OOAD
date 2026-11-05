package com.obfds.exception;

public class InsufficientFundsException extends BankingException {
    public InsufficientFundsException(String accountNumber, double requested, double available) {
        super(String.format("Insufficient funds in %s: requested PKR %,.2f, available PKR %,.2f",
                accountNumber, requested, available));
    }
}

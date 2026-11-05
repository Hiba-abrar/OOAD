package com.obfds.model;

/** Savings account: funds above a fixed minimum balance may be withdrawn. */
public class SavingsAccount extends Account {
    public static final double MINIMUM_BALANCE = 5_000;

    public SavingsAccount(String accountNumber, Customer owner, double openingBalance, String homeCountry) {
        super(accountNumber, owner, openingBalance, homeCountry);
    }

    @Override
    public AccountType getType() {
        return AccountType.SAVINGS;
    }

    @Override
    protected double availableForDebit() {
        return Math.max(0, getBalance() - MINIMUM_BALANCE);
    }
}

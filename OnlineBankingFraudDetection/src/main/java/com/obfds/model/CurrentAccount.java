package com.obfds.model;

/** Current account: may go overdrawn up to an overdraft limit. */
public class CurrentAccount extends Account {
    public static final double DEFAULT_OVERDRAFT_LIMIT = 50_000;

    private final double overdraftLimit;

    public CurrentAccount(String accountNumber, Customer owner, double openingBalance,
                          String homeCountry, double overdraftLimit) {
        super(accountNumber, owner, openingBalance, homeCountry);
        this.overdraftLimit = overdraftLimit;
    }

    @Override
    public AccountType getType() {
        return AccountType.CURRENT;
    }

    @Override
    protected double availableForDebit() {
        return getBalance() + overdraftLimit;
    }
}

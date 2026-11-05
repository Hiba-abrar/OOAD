package com.obfds.model;

import com.obfds.exception.InsufficientFundsException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Base class for all accounts. Subclasses define their debit policy through availableForDebit().
 * The account owns its transaction history (composition): transactions live only inside it.
 */
public abstract class Account {
    private final String accountNumber;
    private final Customer owner;
    private final String homeCountry;
    private double balance;
    private final List<Transaction> transactions = new ArrayList<>();

    protected Account(String accountNumber, Customer owner, double openingBalance, String homeCountry) {
        if (owner == null) {
            throw new IllegalArgumentException("Account must have an owner");
        }
        if (openingBalance < 0) {
            throw new IllegalArgumentException("Opening balance cannot be negative");
        }
        this.accountNumber = accountNumber;
        this.owner = owner;
        this.balance = openingBalance;
        this.homeCountry = homeCountry;
    }

    public abstract AccountType getType();

    /** Policy hook: the maximum amount that may be debited right now. */
    protected abstract double availableForDebit();

    public void deposit(double amount) {
        requirePositive(amount);
        balance += amount;
    }

    public void debit(double amount) {
        requirePositive(amount);
        double available = availableForDebit();
        if (amount > available) {
            throw new InsufficientFundsException(accountNumber, amount, available);
        }
        balance -= amount;
    }

    public void recordTransaction(Transaction transaction) {
        transactions.add(transaction);
    }

    public String getAccountNumber() { return accountNumber; }
    public Customer getOwner() { return owner; }
    public String getHomeCountry() { return homeCountry; }
    public double getBalance() { return balance; }

    /** Amount currently available to debit, including overdraft or minimum-balance policy. */
    public double getAvailableBalance() { return availableForDebit(); }
    public List<Transaction> getTransactions() { return Collections.unmodifiableList(transactions); }

    private static void requirePositive(double amount) {
        if (amount <= 0) {
            throw new IllegalArgumentException("Amount must be positive");
        }
    }

    @Override
    public String toString() {
        return String.format("%s %s %s balance=PKR %,.2f", accountNumber, getType(),
                owner.getFullName(), balance);
    }
}

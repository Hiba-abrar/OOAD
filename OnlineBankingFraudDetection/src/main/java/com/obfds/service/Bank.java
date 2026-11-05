package com.obfds.service;

import com.obfds.exception.AccountNotFoundException;
import com.obfds.exception.BankingException;
import com.obfds.factory.AccountFactory;
import com.obfds.model.Account;
import com.obfds.model.AccountType;
import com.obfds.model.Customer;
import com.obfds.validation.InputValidator;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

/** Holds customers and accounts. The bank aggregates both; neither is owned by the other. */
public class Bank {
    private final String name;
    private final Map<String, Customer> customers = new LinkedHashMap<>();
    private final Map<String, Account> accounts = new LinkedHashMap<>();
    private final AtomicInteger customerSequence = new AtomicInteger(1000);
    private final AtomicInteger accountSequence = new AtomicInteger(5000);

    public Bank(String name) {
        this.name = name;
    }

    public Customer registerCustomer(String fullName, String nationalId) {
        Customer customer = new Customer("CUS-" + customerSequence.incrementAndGet(), fullName, nationalId);
        customers.put(customer.getCustomerId(), customer);
        return customer;
    }

    public Account openAccount(String customerId, AccountType type, double openingBalance, String homeCountry) {
        Customer owner = getCustomer(customerId);
        String country = InputValidator.countryCode(homeCountry);
        String number = "ACC" + accountSequence.incrementAndGet();
        Account account = AccountFactory.create(type, number, owner, openingBalance, country);
        owner.addAccount(account);
        accounts.put(number, account);
        return account;
    }

    public Customer getCustomer(String customerId) {
        Customer customer = customers.get(customerId);
        if (customer == null) {
            throw new BankingException("Customer not found: " + customerId);
        }
        return customer;
    }

    public Account getAccount(String accountNumber) {
        Account account = accounts.get(accountNumber);
        if (account == null) {
            throw new AccountNotFoundException(accountNumber);
        }
        return account;
    }

    /** Returns the account if it is held at this bank, otherwise null (no exception). */
    public Account findAccount(String accountNumber) {
        return accounts.get(accountNumber);
    }

    public Collection<Customer> getCustomers() { return customers.values(); }
    public Collection<Account> getAccounts() { return accounts.values(); }
    public String getName() { return name; }
}

package com.obfds.model;

import java.util.ArrayList;
import java.util.Collections;
import com.obfds.validation.InputValidator;
import java.util.List;

/** A bank customer. Customers aggregate their accounts, which exist independently of them. */
public class Customer {
    private final String customerId;
    private final String fullName;
    private final String nationalId;
    private final List<Account> accounts = new ArrayList<>();

    public Customer(String customerId, String fullName, String nationalId) {
        this.customerId = customerId;
        this.fullName = InputValidator.name(fullName);
        this.nationalId = InputValidator.nationalId(nationalId);
    }

    public void addAccount(Account account) { accounts.add(account); }

    public List<Account> getAccounts() { return Collections.unmodifiableList(accounts); }
    public String getCustomerId() { return customerId; }
    public String getFullName() { return fullName; }

    /** National ID with all but the last four characters masked, for safe display. */
    public String getMaskedNationalId() {
        if (nationalId.length() <= 4) {
            return "****";
        }
        return "*".repeat(nationalId.length() - 4) + nationalId.substring(nationalId.length() - 4);
    }

    @Override
    public String toString() {
        return customerId + " " + fullName;
    }
}

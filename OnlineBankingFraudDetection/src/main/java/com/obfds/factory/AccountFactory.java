package com.obfds.factory;

import com.obfds.model.Account;
import com.obfds.model.AccountType;
import com.obfds.model.CurrentAccount;
import com.obfds.model.Customer;
import com.obfds.model.SavingsAccount;

/** Factory that hides which concrete account class is created for a given AccountType. */
public final class AccountFactory {
    private AccountFactory() {
    }

    public static Account create(AccountType type, String accountNumber, Customer owner,
                                 double openingBalance, String homeCountry) {
        return switch (type) {
            case SAVINGS -> new SavingsAccount(accountNumber, owner, openingBalance, homeCountry);
            case CURRENT -> new CurrentAccount(accountNumber, owner, openingBalance, homeCountry,
                    CurrentAccount.DEFAULT_OVERDRAFT_LIMIT);
        };
    }
}

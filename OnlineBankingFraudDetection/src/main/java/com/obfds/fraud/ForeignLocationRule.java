package com.obfds.fraud;

import com.obfds.model.Account;
import com.obfds.model.Alert;
import com.obfds.model.Severity;
import com.obfds.model.Transaction;
import java.util.Optional;

/** Flags outgoing movements initiated outside the account's home country. */
public class ForeignLocationRule implements FraudRule {
    @Override
    public String getName() {
        return "FOREIGN_LOCATION";
    }

    @Override
    public String getDescription() {
        return "Any withdrawal or transfer started from a country other than the account's home country (PK), "
                + "including very large amounts: MEDIUM (flagged).";
    }

    @Override
    public Optional<Alert> evaluate(Transaction tx, Account account) {
        String location = tx.getLocation();
        if (!tx.getType().isOutgoing() || location == null
                || location.equalsIgnoreCase(account.getHomeCountry())) {
            return Optional.empty();
        }
        String message = String.format("Transaction initiated from %s; home country is %s",
                location, account.getHomeCountry());
        return Optional.of(new Alert(getName(), Severity.MEDIUM, message, tx));
    }
}

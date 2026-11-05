package com.obfds.fraud;

import com.obfds.model.Account;
import com.obfds.model.Alert;
import com.obfds.model.Transaction;
import java.util.Optional;

/** Strategy interface: each implementation encodes one fraud detection rule. */
public interface FraudRule {
    String getName();

    /** Plain-language description of the condition, shown to users in the UI. */
    String getDescription();

    /** Returns an alert when the transaction matches this rule, otherwise an empty Optional. */
    Optional<Alert> evaluate(Transaction transaction, Account account);
}

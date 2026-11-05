package com.obfds.fraud;

import com.obfds.model.Account;
import com.obfds.model.Alert;
import com.obfds.model.Severity;
import com.obfds.model.Transaction;
import com.obfds.model.TransactionType;
import java.util.Optional;

/** Flags a large transfer to an account that is not held at this bank. */
public class ExternalTransferRule implements FraudRule {
    private final double threshold;

    public ExternalTransferRule(double threshold) {
        this.threshold = threshold;
    }

    @Override
    public String getName() {
        return "EXTERNAL_LARGE_TRANSFER";
    }

    @Override
    public String getDescription() {
        return String.format("Transfer of PKR %,.0f or more to an account outside this bank: HIGH (blocked).",
                threshold);
    }

    @Override
    public Optional<Alert> evaluate(Transaction tx, Account account) {
        if (tx.getType() != TransactionType.TRANSFER_OUT || !tx.isExternalCounterparty()
                || tx.getAmount() < threshold) {
            return Optional.empty();
        }
        String message = String.format("PKR %,.2f to account %s outside this bank (threshold PKR %,.2f)",
                tx.getAmount(), tx.getCounterpartyAccount(), threshold);
        return Optional.of(new Alert(getName(), Severity.HIGH, message, tx));
    }
}

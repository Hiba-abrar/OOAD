package com.obfds.fraud;

import com.obfds.model.Account;
import com.obfds.model.Alert;
import com.obfds.model.Severity;
import com.obfds.model.Transaction;
import java.util.Optional;

/** Flags outgoing movements at or above a threshold; twice the threshold is CRITICAL. */
public class HighAmountRule implements FraudRule {
    private final double threshold;

    public HighAmountRule(double threshold) {
        this.threshold = threshold;
    }

    @Override
    public String getName() {
        return "HIGH_AMOUNT";
    }

    @Override
    public String getDescription() {
        return String.format("Any withdrawal or transfer of PKR %,.0f or more: HIGH (blocked). "
                + "PKR %,.0f or more: CRITICAL (blocked).", threshold, 2 * threshold);
    }

    @Override
    public Optional<Alert> evaluate(Transaction tx, Account account) {
        if (!tx.getType().isOutgoing() || tx.getAmount() < threshold) {
            return Optional.empty();
        }
        Severity severity = tx.getAmount() >= 2 * threshold ? Severity.CRITICAL : Severity.HIGH;
        String message = String.format("Outgoing amount PKR %,.2f exceeds threshold PKR %,.2f",
                tx.getAmount(), threshold);
        return Optional.of(new Alert(getName(), severity, message, tx));
    }
}

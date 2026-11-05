package com.obfds.fraud;

import com.obfds.model.Account;
import com.obfds.model.Alert;
import com.obfds.model.Severity;
import com.obfds.model.Transaction;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.Optional;

/** Flags an account that makes too many outgoing movements inside a short time window. */
public class VelocityRule implements FraudRule {
    private final int maxTransactions;
    private final Duration window;

    public VelocityRule(int maxTransactions, Duration window) {
        this.maxTransactions = maxTransactions;
        this.window = window;
    }

    @Override
    public String getName() {
        return "VELOCITY";
    }

    @Override
    public String getDescription() {
        return String.format("More than %d outgoing withdrawals or transfers from one account within %d minutes: "
                + "HIGH (blocked). Blocked and failed attempts count too.", maxTransactions, window.toMinutes());
    }

    @Override
    public Optional<Alert> evaluate(Transaction tx, Account account) {
        if (!tx.getType().isOutgoing()) {
            return Optional.empty();
        }
        LocalDateTime windowStart = tx.getTimestamp().minus(window);
        long priorOutgoing = account.getTransactions().stream()
                .filter(t -> t.getType().isOutgoing())
                .filter(t -> !t.getTimestamp().isBefore(windowStart) && !t.getTimestamp().isAfter(tx.getTimestamp()))
                .count();
        long total = priorOutgoing + 1; // include the transaction being assessed
        if (total <= maxTransactions) {
            return Optional.empty();
        }
        String message = String.format("%d outgoing transactions within %d minutes (limit %d)",
                total, window.toMinutes(), maxTransactions);
        return Optional.of(new Alert(getName(), Severity.HIGH, message, tx));
    }
}

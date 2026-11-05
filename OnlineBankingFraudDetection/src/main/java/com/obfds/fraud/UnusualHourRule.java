package com.obfds.fraud;

import com.obfds.model.Account;
import com.obfds.model.Alert;
import com.obfds.model.Severity;
import com.obfds.model.Transaction;
import java.util.Optional;

/** Flags sizeable outgoing movements during the night window (00:00 to 05:00). */
public class UnusualHourRule implements FraudRule {
    private static final int NIGHT_START = 0;
    private static final int NIGHT_END = 5;

    private final double minAmount;

    public UnusualHourRule(double minAmount) {
        this.minAmount = minAmount;
    }

    @Override
    public String getName() {
        return "UNUSUAL_HOUR";
    }

    @Override
    public String getDescription() {
        return String.format("Withdrawal or transfer of PKR %,.0f or more between %02d:00 and %02d:00: "
                + "LOW (flagged).", minAmount, NIGHT_START, NIGHT_END);
    }

    @Override
    public Optional<Alert> evaluate(Transaction tx, Account account) {
        int hour = tx.getTimestamp().getHour();
        boolean night = hour >= NIGHT_START && hour < NIGHT_END;
        if (!tx.getType().isOutgoing() || !night || tx.getAmount() < minAmount) {
            return Optional.empty();
        }
        String message = String.format("Outgoing PKR %,.2f at %02d:%02d (night window %02d:00-%02d:00)",
                tx.getAmount(), hour, tx.getTimestamp().getMinute(), NIGHT_START, NIGHT_END);
        return Optional.of(new Alert(getName(), Severity.LOW, message, tx));
    }
}

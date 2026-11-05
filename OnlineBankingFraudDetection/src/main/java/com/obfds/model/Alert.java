package com.obfds.model;

import java.time.LocalDateTime;
import java.util.concurrent.atomic.AtomicLong;

/** A fraud warning raised by one rule against one transaction (aggregation of the Transaction). */
public class Alert {
    private static final AtomicLong SEQUENCE = new AtomicLong(1000);

    private final long alertId;
    private final String ruleName;
    private final Severity severity;
    private final String message;
    private final Transaction transaction;

    public Alert(String ruleName, Severity severity, String message, Transaction transaction) {
        this.alertId = SEQUENCE.incrementAndGet();
        this.ruleName = ruleName;
        this.severity = severity;
        this.message = message;
        this.transaction = transaction;
    }

    public long getAlertId() { return alertId; }
    public String getRuleName() { return ruleName; }
    public Severity getSeverity() { return severity; }
    public String getMessage() { return message; }
    public Transaction getTransaction() { return transaction; }
    public LocalDateTime getRaisedAt() { return transaction.getTimestamp(); }

    @Override
    public String toString() {
        return String.format("ALERT #%d [%s] %s: %s (acct %s, tx %s)", alertId, severity, ruleName,
                message, transaction.getAccountNumber(), transaction.getTransactionId());
    }
}

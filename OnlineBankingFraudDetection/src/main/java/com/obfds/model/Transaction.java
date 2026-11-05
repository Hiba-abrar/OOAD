package com.obfds.model;

import java.time.LocalDateTime;
import com.obfds.validation.InputValidator;
import java.util.UUID;

/** One money movement, recorded against the account it belongs to. */
public class Transaction {
    private final String transactionId;
    private final TransactionType type;
    private final double amount;
    private final String accountNumber;
    private final String counterpartyAccount;
    private final String location;
    private final LocalDateTime timestamp;
    private TransactionStatus status = TransactionStatus.PENDING;
    private int riskScore;
    private boolean externalCounterparty;

    public Transaction(TransactionType type, double amount, String accountNumber,
                       String counterpartyAccount, String location, LocalDateTime timestamp) {
        if (amount <= 0) {
            throw new IllegalArgumentException("Transaction amount must be positive");
        }
        this.transactionId = "TXN-" + UUID.randomUUID().toString().substring(0, 8).toUpperCase();
        this.type = type;
        this.amount = amount;
        this.accountNumber = accountNumber;
        this.counterpartyAccount = counterpartyAccount;
        this.location = location == null ? null : InputValidator.countryCode(location);
        this.timestamp = timestamp;
    }

    public String getTransactionId() { return transactionId; }
    public TransactionType getType() { return type; }
    public double getAmount() { return amount; }
    public String getAccountNumber() { return accountNumber; }
    public String getCounterpartyAccount() { return counterpartyAccount; }
    public String getLocation() { return location; }
    public LocalDateTime getTimestamp() { return timestamp; }
    public TransactionStatus getStatus() { return status; }
    public int getRiskScore() { return riskScore; }

    public void setStatus(TransactionStatus status) { this.status = status; }
    public void setRiskScore(int riskScore) { this.riskScore = riskScore; }

    /** True when the recipient account is not held at this bank. */
    public boolean isExternalCounterparty() { return externalCounterparty; }
    public void setExternalCounterparty(boolean externalCounterparty) { this.externalCounterparty = externalCounterparty; }
}

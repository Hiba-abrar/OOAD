package com.obfds.fraud;

import com.obfds.model.Account;
import com.obfds.model.Alert;
import com.obfds.model.Severity;
import com.obfds.model.Transaction;
import com.obfds.model.TransactionStatus;
import com.obfds.model.TransactionType;
import java.util.Optional;

/**
 * Transfers to the sender's own account number. The first MAX_FLAGGED_SELF_TRANSFERS attempts are
 * flagged; every attempt after that is blocked. Failed attempts (insufficient funds) are not counted.
 */
public class SelfTransferRule implements FraudRule {
    public static final int MAX_FLAGGED_SELF_TRANSFERS = 2;

    @Override
    public String getName() {
        return "SELF_TRANSFER";
    }

    @Override
    public String getDescription() {
        return String.format("Transfer to your own account number: attempts 1 and 2 are MEDIUM (flagged); "
                + "attempt %d onwards is HIGH (blocked).", MAX_FLAGGED_SELF_TRANSFERS + 1);
    }

    @Override
    public Optional<Alert> evaluate(Transaction tx, Account account) {
        if (tx.getType() != TransactionType.TRANSFER_OUT
                || !tx.getAccountNumber().equals(tx.getCounterpartyAccount())) {
            return Optional.empty();
        }
        long earlier = account.getTransactions().stream()
                .filter(t -> t.getType() == TransactionType.TRANSFER_OUT)
                .filter(t -> account.getAccountNumber().equals(t.getCounterpartyAccount()))
                .filter(t -> t.getStatus() != TransactionStatus.FAILED)
                .count();
        int attempt = (int) earlier + 1;
        if (attempt <= MAX_FLAGGED_SELF_TRANSFERS) {
            String message = String.format("Self-transfer #%d from this account to itself (flagged)", attempt);
            return Optional.of(new Alert(getName(), Severity.MEDIUM, message, tx));
        }
        String message = String.format("Self-transfer #%d from this account to itself (blocked: limit of %d reached)",
                attempt, MAX_FLAGGED_SELF_TRANSFERS);
        return Optional.of(new Alert(getName(), Severity.HIGH, message, tx));
    }
}

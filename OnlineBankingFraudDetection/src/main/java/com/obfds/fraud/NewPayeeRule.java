package com.obfds.fraud;

import com.obfds.model.Account;
import com.obfds.model.Alert;
import com.obfds.model.Severity;
import com.obfds.model.Transaction;
import com.obfds.model.TransactionStatus;
import com.obfds.model.TransactionType;
import java.util.Optional;

/** Flags a large transfer to a recipient this account has never paid successfully before. */
public class NewPayeeRule implements FraudRule {
    private final double threshold;

    public NewPayeeRule(double threshold) {
        this.threshold = threshold;
    }

    @Override
    public String getName() {
        return "NEW_PAYEE_LARGE";
    }

    @Override
    public String getDescription() {
        return String.format("First successful transfer to a recipient account number, for PKR %,.0f or more: "
                + "MEDIUM (flagged).", threshold);
    }

    @Override
    public Optional<Alert> evaluate(Transaction tx, Account account) {
        if (tx.getType() != TransactionType.TRANSFER_OUT || tx.getAmount() < threshold) {
            return Optional.empty();
        }
        String payee = tx.getCounterpartyAccount();
        if (payee.equals(tx.getAccountNumber())) {
            return Optional.empty(); // self-transfers are handled by SelfTransferRule
        }
        boolean paidBefore = account.getTransactions().stream().anyMatch(t ->
                t.getType() == TransactionType.TRANSFER_OUT
                        && payee.equals(t.getCounterpartyAccount())
                        && t.getStatus() == TransactionStatus.COMPLETED);
        if (paidBefore) {
            return Optional.empty();
        }
        String message = String.format("PKR %,.2f to a recipient never paid before (%s)", tx.getAmount(), payee);
        return Optional.of(new Alert(getName(), Severity.MEDIUM, message, tx));
    }
}

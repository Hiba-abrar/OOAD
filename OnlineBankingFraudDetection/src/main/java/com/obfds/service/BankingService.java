package com.obfds.service;

import com.obfds.exception.InsufficientFundsException;
import com.obfds.fraud.Decision;
import com.obfds.fraud.FraudDetectionEngine;
import com.obfds.fraud.RiskAssessment;
import com.obfds.model.Account;
import com.obfds.model.Transaction;
import com.obfds.model.TransactionStatus;
import com.obfds.model.TransactionType;
import com.obfds.monitoring.AlertPublisher;
import com.obfds.validation.InputValidator;
import java.time.LocalDateTime;

/**
 * Facade over the banking operations. Every outgoing movement is screened by the fraud engine
 * before money moves; blocked movements are recorded but never applied to balances.
 */
public class BankingService {
    private final Bank bank;
    private final FraudDetectionEngine engine;
    private final AlertPublisher publisher;

    public BankingService(Bank bank, FraudDetectionEngine engine, AlertPublisher publisher) {
        this.bank = bank;
        this.engine = engine;
        this.publisher = publisher;
    }

    public Bank getBank() {
        return bank;
    }

    public FraudDetectionEngine getEngine() {
        return engine;
    }

    public Transaction deposit(String accountNumber, double amount) {
        Account account = bank.getAccount(accountNumber);
        account.deposit(amount);
        Transaction tx = new Transaction(TransactionType.DEPOSIT, amount, accountNumber, null,
                account.getHomeCountry(), LocalDateTime.now());
        tx.setStatus(TransactionStatus.COMPLETED);
        account.recordTransaction(tx);
        return tx;
    }

    public Transaction withdraw(String accountNumber, double amount, String location, LocalDateTime when) {
        Account source = bank.getAccount(accountNumber);
        Transaction tx = new Transaction(TransactionType.WITHDRAWAL, amount, accountNumber, null, location, when);
        return executeOutgoing(tx, source, null, amount);
    }

    /**
     * Transfers money to any account number. The recipient may be held at this bank or outside it;
     * for outside recipients only the sender's balance changes here.
     */
    public Transaction transfer(String fromAccount, String toAccount, double amount,
                                String location, LocalDateTime when) {
        Account source = bank.getAccount(fromAccount);
        String recipient = InputValidator.accountNumber(toAccount);
        Account target = bank.findAccount(recipient);
        Transaction tx = new Transaction(TransactionType.TRANSFER_OUT, amount, source.getAccountNumber(),
                recipient, location, when);
        tx.setExternalCounterparty(target == null);
        return executeOutgoing(tx, source, target, amount);
    }

    private Transaction executeOutgoing(Transaction tx, Account source, Account target, double amount) {
        RiskAssessment assessment = engine.assess(tx, source);
        assessment.getAlerts().forEach(publisher::publish);
        tx.setRiskScore(assessment.getRiskScore());

        if (assessment.getDecision() == Decision.BLOCK) {
            tx.setStatus(TransactionStatus.BLOCKED);
            source.recordTransaction(tx);
            return tx;
        }

        try {
            source.debit(amount);
        } catch (InsufficientFundsException e) {
            tx.setStatus(TransactionStatus.FAILED);
            source.recordTransaction(tx);
            throw e;
        }

        if (target != null) {
            target.deposit(amount);
            Transaction inbound = new Transaction(TransactionType.TRANSFER_IN, amount,
                    target.getAccountNumber(), source.getAccountNumber(), tx.getLocation(), tx.getTimestamp());
            inbound.setStatus(TransactionStatus.COMPLETED);
            target.recordTransaction(inbound);
        }

        tx.setStatus(TransactionStatus.COMPLETED);
        source.recordTransaction(tx);
        return tx;
    }
}

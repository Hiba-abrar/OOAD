package com.obfds.fraud;

import com.obfds.model.Account;
import com.obfds.model.Alert;
import com.obfds.model.Severity;
import com.obfds.model.Transaction;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;

/**
 * Runs every registered FraudRule (Strategy pattern) against a transaction and turns the
 * combined alerts into a Decision. HIGH or CRITICAL alerts block; lower alerts flag.
 */
public class FraudDetectionEngine {
    public static final double HIGH_AMOUNT_THRESHOLD = 500_000;
    public static final int VELOCITY_MAX_TRANSACTIONS = 5;
    public static final Duration VELOCITY_WINDOW = Duration.ofMinutes(10);
    public static final double NIGHT_AMOUNT_THRESHOLD = 100_000;
    public static final double NEW_PAYEE_THRESHOLD = 100_000;
    public static final double EXTERNAL_TRANSFER_THRESHOLD = 200_000;

    private static final int MAX_RISK_SCORE = 100;

    private final List<FraudRule> rules = new ArrayList<>();

    public static FraudDetectionEngine withDefaultRules() {
        FraudDetectionEngine engine = new FraudDetectionEngine();
        engine.addRule(new HighAmountRule(HIGH_AMOUNT_THRESHOLD));
        engine.addRule(new VelocityRule(VELOCITY_MAX_TRANSACTIONS, VELOCITY_WINDOW));
        engine.addRule(new ForeignLocationRule());
        engine.addRule(new UnusualHourRule(NIGHT_AMOUNT_THRESHOLD));
        engine.addRule(new SelfTransferRule());
        engine.addRule(new NewPayeeRule(NEW_PAYEE_THRESHOLD));
        engine.addRule(new ExternalTransferRule(EXTERNAL_TRANSFER_THRESHOLD));
        return engine;
    }

    public void addRule(FraudRule rule) {
        rules.add(Objects.requireNonNull(rule, "rule must not be null"));
    }

    public List<FraudRule> getRules() {
        return Collections.unmodifiableList(rules);
    }

    public RiskAssessment assess(Transaction transaction, Account account) {
        List<Alert> alerts = new ArrayList<>();
        for (FraudRule rule : rules) {
            rule.evaluate(transaction, account).ifPresent(alerts::add);
        }
        int score = Math.min(MAX_RISK_SCORE,
                alerts.stream().mapToInt(a -> a.getSeverity().getWeight()).sum());
        return new RiskAssessment(alerts, score, decide(alerts));
    }

    private static Decision decide(List<Alert> alerts) {
        if (alerts.isEmpty()) {
            return Decision.ALLOW;
        }
        boolean serious = alerts.stream().anyMatch(a -> a.getSeverity().compareTo(Severity.HIGH) >= 0);
        return serious ? Decision.BLOCK : Decision.FLAG;
    }
}

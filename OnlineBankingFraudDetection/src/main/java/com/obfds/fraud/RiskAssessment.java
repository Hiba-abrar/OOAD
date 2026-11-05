package com.obfds.fraud;

import com.obfds.model.Alert;
import java.util.Collections;
import java.util.List;

/** Immutable result of screening one transaction. */
public class RiskAssessment {
    private final List<Alert> alerts;
    private final int riskScore;
    private final Decision decision;

    public RiskAssessment(List<Alert> alerts, int riskScore, Decision decision) {
        this.alerts = Collections.unmodifiableList(alerts);
        this.riskScore = riskScore;
        this.decision = decision;
    }

    public List<Alert> getAlerts() { return alerts; }
    public int getRiskScore() { return riskScore; }
    public Decision getDecision() { return decision; }
}

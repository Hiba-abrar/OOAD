package com.obfds.model;

/** Alert severity, ordered from least to most serious. The weight feeds the risk score. */
public enum Severity {
    LOW(5), MEDIUM(20), HIGH(40), CRITICAL(70);

    private final int weight;

    Severity(int weight) {
        this.weight = weight;
    }

    public int getWeight() {
        return weight;
    }
}

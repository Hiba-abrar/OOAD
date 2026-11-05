package com.obfds.app;

import com.obfds.fraud.FraudDetectionEngine;
import com.obfds.monitoring.AlertPublisher;
import com.obfds.monitoring.AuditLogObserver;
import com.obfds.monitoring.ConsoleAlertObserver;
import com.obfds.service.Bank;
import com.obfds.service.BankingService;

/** Composition root: wires the bank, fraud engine, publisher and observers together. */
public final class BankingSystem {
    private final BankingService service;
    private final AuditLogObserver auditLog;

    private BankingSystem(BankingService service, AuditLogObserver auditLog) {
        this.service = service;
        this.auditLog = auditLog;
    }

    public static BankingSystem create(String bankName) {
        AlertPublisher publisher = new AlertPublisher();
        AuditLogObserver auditLog = new AuditLogObserver();
        publisher.subscribe(new ConsoleAlertObserver());
        publisher.subscribe(auditLog);
        BankingService service = new BankingService(new Bank(bankName),
                FraudDetectionEngine.withDefaultRules(), publisher);
        return new BankingSystem(service, auditLog);
    }

    public BankingService getService() { return service; }
    public AuditLogObserver getAuditLog() { return auditLog; }
}

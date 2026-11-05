package com.obfds.monitoring;

import com.obfds.model.Alert;
import com.obfds.model.Severity;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/** Keeps an in-memory audit trail of all alerts for the monitoring dashboard. */
public class AuditLogObserver implements AlertObserver {
    private final List<Alert> log = new ArrayList<>();

    @Override
    public void onAlert(Alert alert) {
        log.add(alert);
    }

    public List<Alert> getAlerts() {
        return Collections.unmodifiableList(log);
    }

    public long countBySeverity(Severity severity) {
        return log.stream().filter(a -> a.getSeverity() == severity).count();
    }
}

package com.obfds.monitoring;

import com.obfds.model.Alert;

/** Prints each alert to the console as it happens. */
public class ConsoleAlertObserver implements AlertObserver {
    @Override
    public void onAlert(Alert alert) {
        System.out.println("      >> " + alert);
    }
}

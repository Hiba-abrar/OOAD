package com.obfds.monitoring;

import com.obfds.model.Alert;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/** Subject in the Observer pattern: broadcasts every raised alert to its subscribers. */
public class AlertPublisher {
    private final List<AlertObserver> observers = new ArrayList<>();

    public void subscribe(AlertObserver observer) {
        observers.add(Objects.requireNonNull(observer, "observer must not be null"));
    }

    public void publish(Alert alert) {
        for (AlertObserver observer : observers) {
            observer.onAlert(alert);
        }
    }
}

package com.obfds.monitoring;

import com.obfds.model.Alert;

/** Observer interface: anything that wants to react to fraud alerts implements this. */
public interface AlertObserver {
    void onAlert(Alert alert);
}

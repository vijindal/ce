package org.ce.app.gui.models;

/**
 * Enumeration representing the state of a calculation.
 */
public enum CalculationState {
    IDLE("Idle"),
    SETUP("Setup"),
    RUNNING("Running"),
    PAUSED("Paused"),
    COMPLETED("Completed"),
    FAILED("Failed"),
    CANCELLED("Cancelled");

    private final String displayName;

    CalculationState(String displayName) {
        this.displayName = displayName;
    }

    public String getDisplayName() {
        return displayName;
    }
}

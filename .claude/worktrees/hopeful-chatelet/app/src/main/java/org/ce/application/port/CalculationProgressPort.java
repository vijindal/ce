package org.ce.application.port;

/**
 * Output port for calculation progress reporting.
 *
 * <p>This port abstracts the progress reporting mechanism so the application
 * layer can report calculation status without coupling to UI-specific classes.
 * Infrastructure/presentation adapters implement this to route updates to
 * GUI widgets, CLI output, or logging frameworks.</p>
 *
 * <p>Implementations should be thread-safe since progress callbacks may fire
 * from background calculation threads.</p>
 *
 * @author CVM Project
 * @version 1.0
 * @since Phase 3 - Application Layer
 */
public interface CalculationProgressPort {

    /**
     * Logs a message during calculation.
     *
     * @param message the message to log (never null)
     */
    void logMessage(String message);

    /**
     * Reports overall calculation progress.
     *
     * @param fraction value between 0.0 (not started) and 1.0 (complete)
     */
    void reportProgress(double fraction);

    /**
     * Signals that calculation has started.
     *
     * @param calculationType human-readable name (e.g., "CVM", "MCS")
     */
    default void onCalculationStarted(String calculationType) {
        logMessage("Starting " + calculationType + " calculation...");
    }

    /**
     * Signals that calculation has completed successfully.
     *
     * @param calculationType human-readable name
     * @param elapsedMs       execution time in milliseconds
     */
    default void onCalculationCompleted(String calculationType, long elapsedMs) {
        logMessage(calculationType + " completed in " + elapsedMs + " ms");
        reportProgress(1.0);
    }

    /**
     * Signals that calculation has failed.
     *
     * @param calculationType human-readable name
     * @param error           the exception that caused failure
     */
    default void onCalculationFailed(String calculationType, Exception error) {
        logMessage(calculationType + " failed: " + error.getMessage());
        reportProgress(0.0);
    }

    /**
     * A no-op implementation for testing or when progress is not needed.
     */
    CalculationProgressPort NO_OP = new CalculationProgressPort() {
        @Override
        public void logMessage(String message) {
            // Intentionally empty
        }

        @Override
        public void reportProgress(double fraction) {
            // Intentionally empty
        }
    };
}


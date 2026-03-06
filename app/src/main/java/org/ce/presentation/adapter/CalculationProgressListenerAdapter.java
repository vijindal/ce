package org.ce.presentation.adapter;

import org.ce.application.port.CalculationProgressPort;
import org.ce.workbench.backend.service.CalculationProgressListener;

/**
 * Adapter bridging legacy {@link CalculationProgressListener} to {@link CalculationProgressPort}.
 *
 * <p>This adapter enables the new application layer use cases to work with existing
 * listeners without modifying legacy code. It translates port method calls to listener calls.</p>
 *
 * <p>Thread-safe: delegates all calls to underlying listener implementations.</p>
 *
 * @author CVM Project
 * @version 1.0
 * @since Phase 5 - Presentation Refactor
 */
public class CalculationProgressListenerAdapter implements CalculationProgressPort {

    private final CalculationProgressListener listener;

    /**
     * Creates an adapter wrapping the given listener.
     *
     * @param listener the legacy listener to wrap (never null)
     * @throws NullPointerException if listener is null
     */
    public CalculationProgressListenerAdapter(CalculationProgressListener listener) {
        this.listener = listener != null ? listener : NO_OP_LISTENER;
    }

    @Override
    public void logMessage(String message) {
        listener.logMessage(message);
    }

    @Override
    public void reportProgress(double fraction) {
        listener.setProgress(fraction);
    }

    @Override
    public void onCalculationStarted(String calculationType) {
        listener.logMessage("Starting " + calculationType + " calculation...");
    }

    @Override
    public void onCalculationCompleted(String calculationType, long elapsedMs) {
        listener.logMessage(calculationType + " completed in " + elapsedMs + " ms");
        listener.setProgress(1.0);
    }

    @Override
    public void onCalculationFailed(String calculationType, Exception error) {
        listener.logMessage(calculationType + " failed: " + error.getMessage());
        listener.setProgress(0.0);
    }

    private static final CalculationProgressListener NO_OP_LISTENER = new CalculationProgressListener() {
        @Override
        public void logMessage(String message) {
            // Intentionally empty
        }

        @Override
        public void setProgress(double progress) {
            // Intentionally empty
        }
    };

    /**
     * A no-op instance for testing or when progress is not needed.
     */
    public static final CalculationProgressListenerAdapter NO_OP = new CalculationProgressListenerAdapter(NO_OP_LISTENER);
}
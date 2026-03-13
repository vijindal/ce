package org.ce.application.port;

import org.ce.application.job.AssemblyResult;

/**
 * Listener for CEC assembly lifecycle events produced by
 * {@link org.ce.application.job.CECAssemblyJob}.
 *
 * <p>All callbacks are invoked on the job's background thread.
 * Implementations that update JavaFX nodes <em>must</em> wrap any UI work in
 * {@code Platform.runLater()}.
 *
 * <p>Default implementations are no-ops so that partial listeners only need to
 * override the callbacks they care about.
 */
public interface CECOperationListener {

    /**
     * Fired once, immediately before any subsystem CEC is loaded.
     *
     * @param targetSystemId  the ID of the system being assembled
     * @param subsystemCount  total number of subsystems that will be processed
     */
    default void onAssemblyStarted(String targetSystemId, int subsystemCount) {}

    /**
     * Fired after each individual subsystem CEC has been loaded and transformed.
     *
     * @param subsystemKey  element string of the subsystem (e.g. {@code "Nb-Ti"})
     * @param order         component count of the subsystem (e.g. {@code 2} for binary)
     */
    default void onSubsystemProcessed(String subsystemKey, int order) {}

    /**
     * Fired on successful completion with the immutable result record.
     *
     * @param result  all outputs from the assembly run
     */
    default void onAssemblyCompleted(AssemblyResult result) {}

    /**
     * Fired when assembly cannot be completed.
     *
     * @param errorMessage  human-readable description of the failure
     */
    default void onAssemblyFailed(String errorMessage) {}
}

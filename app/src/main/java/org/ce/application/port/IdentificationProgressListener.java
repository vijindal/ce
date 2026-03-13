package org.ce.application.port;

import org.ce.domain.model.data.AllClusterData;

/**
 * Listener for stage-level progress events emitted by
 * {@link org.ce.application.job.CFIdentificationJob}.
 *
 * <p>Provides finer-grained feedback than {@code JobListener}: callers receive
 * a callback after each of the three identification stages completes, rather
 * than only at the very end.  This allows the log console and any progress
 * indicator to display intermediate results (e.g. {@code "Stage 2: ncf=4, tcf=15"})
 * during runs that may take 10–30 seconds.
 *
 * <p>All callbacks are invoked on the job's background thread.
 * Implementations that update JavaFX nodes <em>must</em> wrap any UI work in
 * {@code Platform.runLater()}.
 *
 * <p>Default implementations are no-ops.
 */
public interface IdentificationProgressListener {

    /**
     * Fired immediately before a stage begins execution.
     *
     * @param stage        stage number: 1 (cluster ID), 2 (CF ID), or 3 (C-matrix)
     * @param description  short human-readable label, e.g. {@code "Cluster identification"}
     */
    default void onStageStarted(int stage, String description) {}

    /**
     * Fired after a stage completes successfully.
     *
     * @param stage    stage number (1–3)
     * @param summary  one-line summary of key outputs, e.g. {@code "Stage 2: ncf=4, tcf=15"}
     */
    default void onStageCompleted(int stage, String summary) {}

    /**
     * Fired once when all three stages have completed and the result is persisted.
     *
     * @param result  the complete {@link AllClusterData} produced by the pipeline
     */
    default void onPipelineCompleted(AllClusterData result) {}

    /**
     * Fired if the pipeline fails at any point.
     *
     * @param errorMessage  human-readable description of the failure
     */
    default void onPipelineFailed(String errorMessage) {}

    /**
     * Optional: receive individual log lines from the pipeline.
     *
     * <p>The default implementation discards the message; override to route
     * output to a log panel or console.
     *
     * @param message  the log line
     */
    default void logMessage(String message) {}
}

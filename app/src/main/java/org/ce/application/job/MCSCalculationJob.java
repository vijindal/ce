package org.ce.application.job;

import org.ce.application.mcs.MCSCalculationUseCase;
import org.ce.application.port.MCSRunnerPort;
import org.ce.domain.model.result.CalculationFailure;
import org.ce.domain.model.result.CalculationResult;
import org.ce.domain.model.result.MCSResult;
import org.ce.application.service.CalculationProgressListener;
import org.ce.infrastructure.adapter.MCSProgressListenerAdapter;
import org.ce.infrastructure.context.MCSCalculationContext;
import org.ce.infrastructure.mcs.MCSRunnerAdapter;

import java.util.UUID;
import java.util.concurrent.CancellationException;

/**
 * Background job for Monte Carlo Simulation (MCS) calculations.
 *
 * <p>Wraps {@link MCSCalculationUseCase} execution in a managed job that supports:</p>
 * <ul>
 *   <li>Progress tracking via {@link AbstractBackgroundJob}</li>
 *   <li>Cooperative cancellation via {@code cancelled} flag</li>
 *   <li>Proper lifecycle management through {@link BackgroundJobManager}</li>
 * </ul>
 *
 * <p>The job supports both cancellation and pause:</p>
 * <ul>
 *   <li>Cancellation: checked at the start of each Monte Carlo sweep via {@code cancelled} flag</li>
 *   <li>Pause: checked after each sweep in the progress callback via {@code checkPausePoint()}</li>
 * </ul>
 *
 * <p>Both allow clean interruption at natural boundaries without corrupting
 * calculation state.</p>
 */
public class MCSCalculationJob extends AbstractBackgroundJob {

    private final MCSCalculationContext context;
    private final CalculationProgressListener externalListener;
    private CalculationResult result;

    /**
     * Creates a new MCS calculation job.
     *
     * @param context The MCS calculation context containing all parameters
     * @param externalListener Optional external listener for progress updates (e.g., GUI)
     */
    public MCSCalculationJob(
            MCSCalculationContext context,
            CalculationProgressListener externalListener) {
        super(
            "mcs-" + context.getSystem().getId() + "-" + UUID.randomUUID(),
            "MCS: " + context.getSystem().getName() + 
                " (T=" + context.getTemperature() + "K, x=" + context.getComposition() + ")",
            context.getSystem()
        );
        this.context = context;
        this.externalListener = externalListener;
    }

    @Override
    public void run() {
        if (shouldStop()) return;

        try {
            running = true;
            setStatusMessage("Initializing MCS calculation...");
            setProgress(5);

            if (shouldStop()) return;

            // Adapt legacy listener to application port and delegate to use case
            MCSProgressListenerAdapter progressPort = new MCSProgressListenerAdapter(externalListener);
            MCSRunnerPort runnerPort = new MCSRunnerAdapter();
            MCSCalculationUseCase useCase = new MCSCalculationUseCase(progressPort, runnerPort);

            setStatusMessage("Starting Monte Carlo simulation...");
            result = useCase.execute(context, () -> cancelled);

            if (shouldStop()) return;

            if (result instanceof MCSResult) {
                setProgress(100);
                setStatusMessage("MCS calculation completed");
                markCompleted();
            } else if (result instanceof CalculationFailure failure) {
                markFailed(failure.errorMessage());
            } else {
                markFailed("MCS calculation returned unexpected result type");
            }

        } catch (CancellationException ex) {
            // Job was cancelled - this is expected behavior
            setStatusMessage("Calculation cancelled");
            // Don't call markFailed - let the cancel() method handle the state
            
        } catch (Exception e) {
            markFailed("MCS calculation failed: " + e.getMessage());
            e.printStackTrace();
        } finally {
            running = false;
        }
    }

    /**
     * Returns the calculation result.
     *
     * @return CalculationResult if completed, null otherwise
     */
    public CalculationResult getResult() {
        return result;
    }

    /**
     * Returns the calculation context.
     *
     * @return the MCS calculation context
     */
    public MCSCalculationContext getContext() {
        return context;
    }

    @Override
    public String toString() {
        return name + " [" + getProgress() + "%]";
    }
}


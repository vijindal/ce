package org.ce.infrastructure.service;

import org.ce.application.port.MCSProgressPort;
import org.ce.application.port.MCSProgressPort.SimulationPhase;
import org.ce.domain.mcs.MCSUpdate;
import org.ce.application.port.CalculationProgressListener;

/**
 * Adapter bridging legacy {@link CalculationProgressListener} to {@link MCSProgressPort}.
 *
 * <p>This adapter enables MCS simulations using the new application layer to work
 * with existing UI listeners. It translates MCS-specific port calls to listener calls.</p>
 *
 * <p>Thread-safe: delegates all calls to underlying listener implementations.</p>
 *
 * @author CVM Project
 * @version 1.0
 * @since Phase 5 - Presentation Refactor
 */
public class MCSProgressListenerAdapter extends CalculationProgressListenerAdapter implements MCSProgressPort {

    private final CalculationProgressListener listener;

    /**
     * Creates an adapter wrapping the given listener for MCS progress reporting.
     *
     * @param listener the legacy listener to wrap (never null)
     * @throws NullPointerException if listener is null
     */
    public MCSProgressListenerAdapter(CalculationProgressListener listener) {
        super(listener);
        this.listener = listener != null ? listener : NO_OP_LISTENER;
    }

    @Override
    public void initializeMCS(int equilibrationSteps, int averagingSteps, long seed) {
        listener.initializeMCS(equilibrationSteps, averagingSteps, seed);
    }

    @Override
    public void onMCSUpdate(MCSSnapshot snapshot) {
        // Convert from new MCSSnapshot format to legacy MCSUpdate
        // Map SimulationPhase â†’ MCSUpdate.Phase
        MCSUpdate.Phase phase = snapshot.phase() == SimulationPhase.AVERAGING
                ? MCSUpdate.Phase.AVERAGING
                : MCSUpdate.Phase.EQUILIBRATION;

        long now = System.currentTimeMillis();
        MCSUpdate update = new MCSUpdate(
                snapshot.step(),
                snapshot.totalEnergy(),
                snapshot.deltaEnergy(),
                snapshot.sigmaDeltaE(),
                snapshot.meanDeltaE(),
                phase,
                snapshot.acceptanceRate(),
                now,
                snapshot.elapsedMs()
        );
        listener.updateMCSData(update);
    }

    // No-op listener for null safety
    private static final CalculationProgressListener NO_OP_LISTENER = new CalculationProgressListener() {
        @Override
        public void logMessage(String message) {
            // Intentionally empty
        }

        @Override
        public void setProgress(double progress) {
            // Intentionally empty
        }

        @Override
        public void initializeMCS(int equilibrationSteps, int averagingSteps, long seed) {
            // Intentionally empty
        }

        @Override
        public void updateMCSData(MCSUpdate update) {
            // Intentionally empty
        }
    };
}

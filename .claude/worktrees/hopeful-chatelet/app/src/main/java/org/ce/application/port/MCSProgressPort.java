package org.ce.application.port;

/**
 * Extended progress port for MCS-specific real-time updates.
 *
 * <p>MCS simulations emit periodic updates during equilibration and averaging
 * phases. This interface provides hooks for GUI visualization components
 * (charts, progress indicators) to receive these updates.</p>
 *
 * @author CVM Project
 * @version 1.0
 * @since Phase 3 - Application Layer
 */
public interface MCSProgressPort extends CalculationProgressPort {

    /**
     * Phase of the Monte Carlo simulation.
     */
    enum SimulationPhase {
        EQUILIBRATION,
        AVERAGING
    }

    /**
     * Snapshot of MCS state at a given step.
     *
     * @param step           current MC sweep number
     * @param totalEnergy    cumulative energy (E_initial + Î£(Î”E))
     * @param deltaEnergy    energy change for this step
     * @param sigmaDeltaE    Ïƒ(Î”E) over rolling window (stability metric)
     * @param meanDeltaE     mean(Î”E) over rolling window (drift check)
     * @param phase          EQUILIBRATION or AVERAGING
     * @param acceptanceRate fraction of accepted moves [0,1]
     * @param elapsedMs      wall-clock milliseconds since start
     */
    record MCSSnapshot(
            int step,
            double totalEnergy,
            double deltaEnergy,
            double sigmaDeltaE,
            double meanDeltaE,
            SimulationPhase phase,
            double acceptanceRate,
            long elapsedMs
    ) {}

    /**
     * Called once before MCS simulation starts to initialize UI components.
     *
     * @param equilibrationSteps number of equilibration sweeps
     * @param averagingSteps     number of averaging sweeps
     * @param seed               random seed for reproducibility
     */
    void initializeMCS(int equilibrationSteps, int averagingSteps, long seed);

    /**
     * Called periodically with MCS state updates.
     *
     * @param snapshot the current simulation state
     */
    void onMCSUpdate(MCSSnapshot snapshot);

    /**
     * A no-op implementation for testing or when GUI updates are not needed.
     */
    MCSProgressPort NO_OP_MCS = new MCSProgressPort() {
        @Override
        public void logMessage(String message) {
            // Intentionally empty
        }

        @Override
        public void reportProgress(double fraction) {
            // Intentionally empty
        }

        @Override
        public void initializeMCS(int equilibrationSteps, int averagingSteps, long seed) {
            // Intentionally empty
        }

        @Override
        public void onMCSUpdate(MCSSnapshot snapshot) {
            // Intentionally empty
        }
    };
}


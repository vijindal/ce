package org.ce.domain.model.result;

/**
 * Engine-specific diagnostics sealed under the unified {@link EquilibriumState}.
 *
 * <p>Carries metadata about the internal equilibrium engine that produced an
 * {@link EquilibriumState}. Consumers can pattern-match to access engine-specific
 * fields without casting the enclosing result type.</p>
 *
 * <h2>Usage</h2>
 * <pre>{@code
 * EquilibriumState state = ...;
 * String info = switch (state.metrics()) {
 *     case EngineMetrics.CvmMetrics cvm ->
 *         "NR converged in " + cvm.iterations() + " iterations";
 *     case EngineMetrics.McsMetrics mcs ->
 *         "MC accept rate: " + String.format("%.1f%%", mcs.acceptRate() * 100);
 * };
 * }</pre>
 *
 * @since 2.1
 */
public sealed interface EngineMetrics
        permits EngineMetrics.CvmMetrics, EngineMetrics.McsMetrics {

    /**
     * Diagnostics for a CVM Newton-Raphson minimization.
     *
     * @param converged     whether the N-R solver converged within tolerance
     * @param iterations    number of N-R iterations performed
     * @param gradientNorm  ‖∇G‖ at the final iteration (convergence measure)
     * @param gradient      ∇G at equilibrium (dG/du for each non-point CF); may be empty array
     * @param hessian       ∇²G at equilibrium (d²G/du_i du_j); may be empty array for stability analysis
     */
    record CvmMetrics(
            boolean converged,
            int iterations,
            double gradientNorm,
            double[] gradient,
            double[][] hessian
    ) implements EngineMetrics {

        /**
         * Compact constructor for backward compatibility (no gradient/hessian).
         */
        public CvmMetrics(boolean converged, int iterations, double gradientNorm) {
            this(converged, iterations, gradientNorm, new double[0], new double[0][]);
        }
    }

    /**
     * Diagnostics for a Metropolis Monte Carlo simulation.
     *
     * @param acceptRate    fraction of proposed moves that were accepted
     * @param nEquilSweeps  number of equilibration sweeps performed
     * @param nAvgSweeps    number of averaging sweeps performed
     * @param supercellSize supercell dimension L (N = 2·L³ for BCC)
     * @param nSites        total number of lattice sites in the supercell
     * @param energyPerSite total energy per site (J/mol) from incremental MCEngine tracking
     */
    record McsMetrics(
            double acceptRate,
            long nEquilSweeps,
            long nAvgSweeps,
            int supercellSize,
            int nSites,
            double energyPerSite
    ) implements EngineMetrics {}
}

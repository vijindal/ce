package org.ce.domain.model.result;

import java.time.Instant;
import java.util.Objects;

/**
 * Immutable result of a Monte Carlo simulation (MCS).
 *
 * <p>Contains ensemble averages of thermodynamic quantities computed from
 * the Metropolis sampling phase.</p>
 *
 * @param temperature          simulation temperature in Kelvin
 * @param compositionArray     composition for each component (x[c] = N_c/N)
 * @param avgCFs               ensemble average correlation functions âŸ¨u_tâŸ©
 * @param energyPerSite        average energy per site (eV or J depending on ECI units)
 * @param heatCapacityPerSite  heat capacity per site from fluctuation formula
 * @param acceptRate           fraction of accepted Monte Carlo moves
 * @param nEquilSweeps         number of equilibration sweeps
 * @param nAvgSweeps           number of averaging sweeps
 * @param supercellSize        supercell dimension L (N = 2Â·LÂ³ for BCC)
 * @param nSites               total number of lattice sites
 * @param timestamp            when this result was computed
 *
 * @since 2.0
 */
public record MCSResult(
        double temperature,
        double[] compositionArray,
        double[] avgCFs,
        double energyPerSite,
        double heatCapacityPerSite,
        double acceptRate,
        long nEquilSweeps,
        long nAvgSweeps,
        int supercellSize,
        int nSites,
        Instant timestamp
) implements ThermodynamicResult {

    /**
     * Canonical constructor with validation.
     */
    public MCSResult {
        Objects.requireNonNull(compositionArray, "compositionArray");
        Objects.requireNonNull(avgCFs, "avgCFs");
        Objects.requireNonNull(timestamp, "timestamp");
        compositionArray = compositionArray.clone();
        avgCFs = avgCFs.clone();
    }

    /**
     * Convenience factory for creating from engine output (uses current time).
     */
    public static MCSResult fromEngine(
            double temperature, double[] composition, double[] avgCFs,
            double energyPerSite, double heatCapacityPerSite,
            double acceptRate, long nEquilSweeps, long nAvgSweeps,
            int supercellSize, int nSites) {
        return new MCSResult(
                temperature, composition.clone(), avgCFs.clone(),
                energyPerSite, heatCapacityPerSite, acceptRate,
                nEquilSweeps, nAvgSweeps, supercellSize, nSites,
                Instant.now()
        );
    }

    /**
     * Returns composition as single B-fraction (for binary systems).
     */
    @Override
    public double composition() {
        return compositionArray.length > 1 ? compositionArray[1] : 0.0;
    }

    @Override
    public double[] correlationFunctions() {
        return avgCFs.clone();
    }

    @Override
    public String summary() {
        return String.format("""
                MCS Result
                â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•
                  Temperature:   %.1f K
                  Composition:   %.4f (x_B)
                  Supercell:     %d Ã— %d Ã— %d (%d sites)
                  Equilibration: %d sweeps
                  Averaging:     %d sweeps
                  Accept Rate:   %.2f%%
                  âŸ¨EâŸ©/site:      %.6e
                  Cv/site:       %.6e
                â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•""",
                temperature, composition(),
                supercellSize, supercellSize, supercellSize, nSites,
                nEquilSweeps, nAvgSweeps,
                acceptRate * 100,
                energyPerSite, heatCapacityPerSite);
    }
}


package org.ce.workbench.backend.data;

/**
 * MCS-specific solver metadata.
 *
 * <p>Captures Monte Carlo sampling statistics and configuration details.</p>
 *
 * @param avgCFs           average correlation function values from sampling
 * @param acceptRate       fraction of accepted MC moves
 * @param heatCapacity     heat capacity per site (Cv/N)
 * @param nEquilSweeps     number of equilibration sweeps performed
 * @param nAvgSweeps       number of averaging sweeps performed
 * @param supercellSize    linear size L of the supercell
 * @param nSites           total number of lattice sites (L³ for 3D)
 * @param wallClockTimeMs  execution time in milliseconds
 */
public record MCSMetadata(
        double[] avgCFs,
        double acceptRate,
        double heatCapacity,
        long nEquilSweeps,
        long nAvgSweeps,
        int supercellSize,
        int nSites,
        long wallClockTimeMs
) implements SolverMetadata {
    
    @Override
    public CalculationMethod method() {
        return CalculationMethod.MCS;
    }
    
    /**
     * Creates MCSMetadata from an MCResult.
     */
    public static MCSMetadata from(org.ce.mcs.MCResult result, long wallClockTimeMs) {
        return new MCSMetadata(
            result.getAvgCFs(),
            result.getAcceptRate(),
            result.getHeatCapacityPerSite(),
            result.getNEquilSweeps(),
            result.getNAvgSweeps(),
            result.getSupercellSize(),
            result.getNSites(),
            wallClockTimeMs
        );
    }
}

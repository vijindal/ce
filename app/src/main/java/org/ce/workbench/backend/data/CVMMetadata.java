package org.ce.workbench.backend.data;

/**
 * CVM-specific solver metadata.
 *
 * <p>Captures Newton-Raphson convergence details and equilibrium
 * correlation function values from a CVM calculation.</p>
 *
 * @param equilibriumCFs equilibrium correlation function values u[0..ncf-1]
 * @param iterations     number of Newton-Raphson iterations performed
 * @param gradientNorm   final gradient norm ||∇G|| at convergence
 * @param converged      whether the solver converged within tolerance
 * @param wallClockTimeMs execution time in milliseconds
 */
public record CVMMetadata(
        double[] equilibriumCFs,
        int iterations,
        double gradientNorm,
        boolean converged,
        long wallClockTimeMs
) implements SolverMetadata {
    
    @Override
    public CalculationMethod method() {
        return CalculationMethod.CVM;
    }
    
    /**
     * Creates CVMMetadata from a CVMSolverResult.
     */
    public static CVMMetadata from(org.ce.cvm.CVMSolverResult result, long wallClockTimeMs) {
        return new CVMMetadata(
            result.getEquilibriumCFs(),
            result.getIterations(),
            result.getGradientNorm(),
            result.isConverged(),
            wallClockTimeMs
        );
    }
}

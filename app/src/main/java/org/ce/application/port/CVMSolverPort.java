package org.ce.application.port;

import org.ce.domain.cvm.CVMModelInput;
import org.ce.domain.model.result.CVMResult;

/**
 * Application port for CVM free-energy minimization.
 *
 * <p>Implemented by infrastructure adapters that bridge to the concrete
 * numerical engine. Supports binary, ternary, and higher-order systems.</p>
 */
public interface CVMSolverPort {

    /**
     * Runs the CVM solver and returns a domain result.
     *
     * @param modelInput cluster topology and symmetry data
     * @param eci effective cluster interactions
     * @param temperature temperature in K
     * @param compositionArray mole fractions for all components (sum ≈ 1.0)
     * @param numComponents number of chemical components
     * @param tolerance Newton-Raphson convergence tolerance
     * @return CVM calculation result
     */
    CVMResult solve(
            CVMModelInput modelInput,
            double[] eci,
            double temperature,
            double[] compositionArray,
            int numComponents,
            double tolerance);
}


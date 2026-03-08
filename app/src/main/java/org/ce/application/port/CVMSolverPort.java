package org.ce.application.port;

import org.ce.domain.cvm.CVMModelInput;
import org.ce.domain.model.result.CVMResult;

/**
 * Application port for CVM free-energy minimization.
 *
 * <p>Implemented by infrastructure adapters that bridge to the concrete
 * numerical engine.</p>
 */
public interface CVMSolverPort {

    /**
     * Runs the CVM solver and returns a domain result.
     */
    CVMResult solve(
            CVMModelInput modelInput,
            double[] eci,
            double temperature,
            double composition,
            double tolerance);
}


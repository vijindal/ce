package org.ce.infrastructure.cvm;

import org.ce.application.port.CVMSolverPort;
import org.ce.domain.cvm.CVMEngine;
import org.ce.domain.cvm.CVMSolverResult;
import org.ce.domain.cvm.CVMModelInput;
import org.ce.domain.model.result.CVMResult;

/**
 * Infrastructure adapter bridging the application CVM port to CVMEngine.
 * Supports binary, ternary, and higher-order systems.
 */
public final class CVMEngineAdapter implements CVMSolverPort {

    @Override
    public CVMResult solve(
            CVMModelInput modelInput,
            double[] eci,
            double temperature,
            double[] compositionArray,
            int numComponents,
            double tolerance) {

        CVMSolverResult solverResult = CVMEngine.solve(
                modelInput,
                eci,
                temperature,
                compositionArray,
                numComponents,
                tolerance);

        // For backward compat with result: use binary composition (x[1]) if K=2
        double compositionScalar = (numComponents == 2) ? compositionArray[1] : Double.NaN;

        return CVMResult.fromSolver(
                temperature,
                compositionScalar,
                solverResult.getEquilibriumCFs(),
                solverResult.getGibbsEnergy(),
                solverResult.getEnthalpy(),
                solverResult.getEntropy(),
                solverResult.getIterations(),
                solverResult.getGradientNorm(),
                solverResult.isConverged());

    }
}


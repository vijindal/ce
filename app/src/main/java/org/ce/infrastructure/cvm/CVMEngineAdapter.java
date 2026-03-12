package org.ce.infrastructure.cvm;

import org.ce.application.port.CVMSolverPort;
import org.ce.domain.cvm.CVMEngine;
import org.ce.domain.cvm.CVMSolverResult;
import org.ce.domain.cvm.CVMModelInput;
import org.ce.domain.model.result.EquilibriumState;

/**
 * Infrastructure adapter bridging the application CVM port to CVMEngine.
 * Supports binary, ternary, and higher-order systems.
 */
public final class CVMEngineAdapter implements CVMSolverPort {

    @Override
    public EquilibriumState solve(
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

        return EquilibriumState.fromCvm(
                temperature,
                compositionArray,
                solverResult.getEquilibriumCFs(),
                solverResult.getEnthalpy(),
                solverResult.getGibbsEnergy(),
                solverResult.getEntropy(),
                solverResult.isConverged(),
                solverResult.getIterations(),
                solverResult.getGradientNorm());
    }
}

package org.ce.infrastructure.cvm;

import org.ce.application.port.CVMSolverPort;
import org.ce.domain.cvm.CVMEngine;
import org.ce.domain.cvm.CVMSolverResult;
import org.ce.domain.cvm.CVMModelInput;
import org.ce.domain.model.result.CVMResult;

/**
 * Infrastructure adapter bridging the application CVM port to CVMEngine.
 */
public final class CVMEngineAdapter implements CVMSolverPort {

    @Override
    public CVMResult solve(
            CVMModelInput modelInput,
            double[] eci,
            double temperature,
            double composition,
            double tolerance) {

        CVMSolverResult solverResult = CVMEngine.solve(
                modelInput,
                eci,
                temperature,
                composition,
                tolerance);

        return CVMResult.fromSolver(
                temperature,
                composition,
                solverResult.getEquilibriumCFs(),
                solverResult.getGibbsEnergy(),
                solverResult.getEnthalpy(),
                solverResult.getEntropy(),
                solverResult.getIterations(),
                solverResult.getGradientNorm(),
                solverResult.isConverged());
    }
}


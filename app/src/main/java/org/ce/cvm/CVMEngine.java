package org.ce.cvm;

import org.ce.identification.cf.CFIdentificationResult;
import org.ce.identification.cluster.ClusterIdentificationResult;
import org.ce.workbench.backend.data.AllClusterData;
import org.ce.workbench.util.context.CVMCalculationContext;

import java.util.List;

/**
 * Top-level orchestrator for CVM free-energy calculations.
 *
 * <p>Takes a fully populated {@link CVMCalculationContext} and runs the
 * Newton-Raphson solver to find the equilibrium correlation functions
 * that minimise the Gibbs energy of mixing.</p>
 *
 * <p>This class bridges the workbench context layer and the core CVM
 * solver. It extracts all necessary arrays from the context and delegates
 * to {@link NewtonRaphsonSolver}.</p>
 *
 * <h2>Usage</h2>
 * <pre>{@code
 * CVMCalculationContext ctx = ...;
 * CVMSolverResult result = CVMEngine.solve(ctx);
 * System.out.println(result.toSummary());
 * }</pre>
 */
public final class CVMEngine {

    private CVMEngine() { /* utility class */ }

    /**
     * Runs the CVM Newton-Raphson solver on the given context.
     *
     * @param context fully validated calculation context
     * @return solver result containing equilibrium CFs and thermodynamic quantities
     * @throws IllegalStateException if context is not ready
     */
    public static CVMSolverResult solve(CVMCalculationContext context) {
        if (!context.isReady()) {
            throw new IllegalStateException(
                    "CVMCalculationContext is not ready: " + context.getReadinessError());
        }

        AllClusterData allData = context.getAllClusterData();
        ClusterIdentificationResult stage1 = allData.getStage1();
        CFIdentificationResult stage2 = allData.getStage2();
        CMatrixResult stage3 = allData.getStage3();

        // Extract arrays
        int tcdis = stage1.getTcdis();
        int tcf   = stage2.getTcf();
        int ncf   = stage2.getNcf();
        double[] kb     = stage1.getKbCoefficients();
        double[][] mh   = stage1.getMh();
        int[] lc         = stage1.getLc();
        List<Double> mhdis = stage1.getDisClusterData().getMultiplicities();

        List<List<double[][]>> cmat = stage3.getCmat();
        int[][] lcv                = stage3.getLcv();
        List<List<int[]>> wcv      = stage3.getWcv();

        double[] eci         = context.getECI();
        double temperature   = context.getTemperature();
        double[] moleFractions = context.getMoleFractions();
        int numElements      = allData.getNumComponents();
        double tolerance     = context.getTolerance();
        int[][] lcf          = stage2.getLcf();
        int[][] cfBasisIndices = stage3.getCfBasisIndices();

        // Run Newton-Raphson solver
        return NewtonRaphsonSolver.solve(
                moleFractions, numElements,
                temperature, eci,
                mhdis, kb, mh, lc,
                cmat, lcv, wcv,
                tcdis, tcf, ncf,
                lcf, cfBasisIndices,
                tolerance);
    }
}

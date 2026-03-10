package org.ce.domain.cvm;

import org.ce.domain.identification.cluster.CFIdentificationResult;
import org.ce.domain.identification.cluster.ClusterIdentificationResult;

import java.util.List;

/**
 * Top-level orchestrator for CVM free-energy calculations.
 *
 * <p>Takes fully populated CVM stage data and runs the
 * Newton-Raphson solver to find the equilibrium correlation functions
 * that minimise the Gibbs energy of mixing.</p>
 *
 * <p>This class extracts all necessary arrays from the model input and delegates
 * to {@link NewtonRaphsonSolverSimple}.</p>
 *
 * <h2>Usage</h2>
 * <pre>{@code
 * CVMModelInput input = ...;
 * CVMSolverResult result = CVMEngine.solve(input, eci, temperature, composition, tolerance);
 * System.out.println(result.toSummary());
 * }</pre>
 */
public final class CVMEngine {

    private CVMEngine() { /* utility class */ }

    /**
     * Runs the CVM Newton-Raphson solver on the given context.
     * Supports binary, ternary, and higher-order systems.
     *
     * @param input CVM stage model input
     * @param eci effective cluster interactions in non-point CF basis
     * @param temperature temperature in Kelvin
     * @param compositionArray mole fractions for all components (sum ≈ 1.0)
     * @param numComponents number of chemical components (≥ 2)
     * @param tolerance convergence tolerance
     * @return solver result containing equilibrium CFs and thermodynamic quantities
     * @throws IllegalArgumentException if input is null or array length mismatches numComponents
     */
    public static CVMSolverResult solve(
            CVMModelInput input,
            double[] eci,
            double temperature,
            double[] compositionArray,
            int numComponents,
            double tolerance) {
        if (input == null) {
            throw new IllegalArgumentException("CVMModelInput must not be null");
        }
        if (compositionArray.length != numComponents) {
            throw new IllegalArgumentException(
                    "compositionArray length (" + compositionArray.length
                    + ") must equal numComponents (" + numComponents + ")");
        }

        ClusterIdentificationResult stage1 = input.getStage1();
        CFIdentificationResult stage2 = input.getStage2();
        CMatrixResult stage3 = input.getStage3();

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

        // Use composition array directly (supports K=2, K=3, K≥4)
        double[] moleFractions = compositionArray.clone();
        int[][] lcf          = stage2.getLcf();
        int[][] cfBasisIndices = stage3.getCfBasisIndices();

        // Run Newton-Raphson solver (simple version based on proven working code)
        return NewtonRaphsonSolverSimple.solve(
                moleFractions, numComponents,
                temperature, eci,
                mhdis, kb, mh, lc,
                cmat, lcv, wcv,
                tcdis, tcf, ncf,
                lcf, cfBasisIndices,
                tolerance);
    }
}



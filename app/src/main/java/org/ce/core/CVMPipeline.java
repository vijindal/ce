package org.ce.core;

import org.ce.cvm.CMatrixBuilder;
import org.ce.cvm.CMatrixResult;
import org.ce.identification.geometry.Cluster;
import org.ce.input.InputLoader;
import org.ce.identification.symmetry.SymmetryOperation;
import org.ce.identification.cluster.ClusterIdentifier;
import org.ce.identification.cluster.ClusterIdentificationResult;
import org.ce.identification.cf.CFIdentifier;
import org.ce.identification.cf.CFIdentificationResult;
import org.ce.identification.geometry.Vector3D;
import java.util.List;

/**
 * Single-entry-point orchestrator for the two-stage CVM identification pipeline.
 *
 * <p>This class implements the complete CVM workflow:
 * <ol>
 *   <li><strong>Stage 1: Cluster Identification</strong> — Determines distinct cluster
 *       types and their properties (multiplicities, Kikuchi-Baker coefficients) for a
 *       given CVM approximation and pair of phases (disordered reference and ordered
 *       target).</li>
 *   <li><strong>Stage 2: Correlation Function Identification</strong> — Enumerates
 *       and groups all distinct correlation functions (decorated clusters) for a
 *       specified number of components.</li>
 * </ol></p>
 *
 * <h2>Usage Example</h2>
 * <pre>{@code
 * // Construct configuration
 * CVMConfiguration config = CVMConfiguration.builder()
 *     .disorderedClusterFile("cluster/A2-T.txt")
 *     .orderedClusterFile("cluster/B2-T.txt")
 *     .disorderedSymmetryGroup("A2-SG")
 *     .orderedSymmetryGroup("B2-SG")
 *     .transformationMatrix(new double[][]{{1,0,0},{0,1,0},{0,0,1}})
 *     .translationVector(new Vector3D(0,0,0))
 *     .numComponents(2)
 *     .build();
 *
 * // Run pipeline
 * CVMResult result = CVMPipeline.identify(config);
 *
 * // Access results
 * result.printDebug();
 * ClusterIdentificationResult stage1 = result.getClusters();
 * CFIdentificationResult stage2 = result.getCorrelationFunctions();
 * }</pre>
 *
 * @author  CVM Project
 * @version 1.0
 * @see     CVMConfiguration
 * @see     CVMResult
 * @see     ClusterIdentifier
 * @see     CFIdentifier
 */
public class CVMPipeline {

    private CVMPipeline() {
        // Utility class: prevent instantiation
    }

    /**
     * Runs the complete two-stage CVM identification pipeline.
     *
     * <p>This method:
     * <ol>
     *   <li>Loads cluster and symmetry data from files specified in {@code config}</li>
     *   <li>Executes Stage 1 (ClusterIdentifier) to determine cluster types and properties</li>
     *   <li>Executes Stage 2 (CFIdentifier) to enumerate correlation functions</li>
     *   <li>Wraps both results in a unified {@link CVMResult}</li>
     * </ol></p>
     *
     * @param config the configuration object specifying files, symmetries, and parameters
     * @return a unified {@link CVMResult} containing both stage outputs
     * @throws IllegalArgumentException if configuration is invalid or files cannot be loaded
     * @throws RuntimeException if either stage fails
     */
    public static CVMResult identify(CVMConfiguration config) {

        if (config == null) {
            throw new IllegalArgumentException("config must not be null");
        }

        // =====================================================================
        // Load inputs from configuration
        // =====================================================================
        List<Cluster> disMaxClus =
                InputLoader.parseClusterFile(config.getDisorderedClusterFile());
        List<Cluster> ordMaxClus =
                InputLoader.parseClusterFile(config.getOrderedClusterFile());

        List<SymmetryOperation> disSymOps =
                InputLoader.parseSymmetryFile(config.getDisorderedSymmetryGroup());
        List<SymmetryOperation> ordSymOps =
                InputLoader.parseSymmetryFile(config.getOrderedSymmetryGroup());

        double[][] rotMat = config.getTransformationMatrix();
        Vector3D transVec = config.getTranslationVector();
        double[] transMat = new double[]{transVec.getX(), transVec.getY(), transVec.getZ()};
        int numComp = config.getNumComponents();

        // =====================================================================
        // Stage 1: Cluster Identification
        // =====================================================================
        ClusterIdentificationResult stage1Result =
                ClusterIdentifier.identify(
                        disMaxClus, disSymOps,
                        ordMaxClus, ordSymOps,
                        rotMat, transMat);

        // =====================================================================
        // Stage 2: Correlation Function Identification
        // =====================================================================
        CFIdentificationResult stage2Result =
                CFIdentifier.identify(
                        stage1Result,
                        disMaxClus, disSymOps,
                        ordMaxClus, ordSymOps,
                        rotMat, transMat,
                        numComp);

        // =====================================================================
        // Wrap and return unified result
        // =====================================================================
        return new CVMResult(stage1Result, stage2Result);
    }

    /**
     * Runs the identification pipeline and builds the C-matrix for the
     * ordered-phase maximal cluster set defined in {@code config}.
     *
     * @param config configuration for the target structure and approximation
     * @return C-matrix result (cmat, lcv, wcv)
     */
    public static CMatrixResult buildCMatrix(CVMConfiguration config) {

        if (config == null) {
            throw new IllegalArgumentException("config must not be null");
        }

        CVMResult result = identify(config);
        List<Cluster> ordMaxClus =
                InputLoader.parseClusterFile(config.getOrderedClusterFile());

        return CMatrixBuilder.build(
                result.getClusterIdentification(),
                result.getCorrelationFunctionIdentification(),
                ordMaxClus,
                config.getNumComponents());
    }

    /**
     * Prints usage information and an example of the CVM pipeline to standard output.
     */
    public static void printUsage() {
        System.out.println(
            "\n" +
            "=========================================================\n" +
            "CVM Pipeline API Usage\n" +
            "=========================================================\n" +
            "\n" +
            "// 1. Create configuration\n" +
            "CVMConfiguration config = CVMConfiguration.builder()\n" +
            "    .disorderedClusterFile(\"cluster/A2-T.txt\")\n" +
            "    .orderedClusterFile(\"cluster/B2-T.txt\")\n" +
            "    .disorderedSymmetryGroup(\"A2-SG\")\n" +
            "    .orderedSymmetryGroup(\"B2-SG\")\n" +
            "    .transformationMatrix(new double[][]{\n" +
            "        {1,0,0},\n" +
            "        {0,1,0},\n" +
            "        {0,0,1}\n" +
            "    })\n" +
            "    .translationVector(new org.ce.identification.geometry.Vector3D(0,0,0))\n" +
            "    .numComponents(2)\n" +
            "    .build();\n" +
            "\n" +
            "// 2. Run pipeline (static method)\n" +
            "CVMResult result = CVMPipeline.identify(config);\n" +
            "\n" +
            "// 3. Access results\n" +
            "result.printDebug();\n" +
            "ClusterIdentificationResult clusters = result.getClusterIdentification();\n" +
            "CFIdentificationResult correlationFunctions = result.getCorrelationFunctionIdentification();\n" +
            "\n" +
            "=========================================================\n"
        );
    }
}

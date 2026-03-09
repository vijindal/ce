package org.ce.application.usecase;

import org.ce.domain.cvm.CMatrixBuilder;
import org.ce.domain.cvm.CMatrixResult;
import org.ce.domain.identification.geometry.Cluster;
import org.ce.infrastructure.input.InputLoader;
import org.ce.domain.identification.symmetry.SymmetryOperation;
import org.ce.domain.identification.cluster.ClusterIdentifier;
import org.ce.domain.identification.cluster.ClusterIdentificationResult;
import org.ce.domain.identification.cluster.CFIdentifier;
import org.ce.domain.identification.cluster.CFIdentificationResult;
import org.ce.domain.identification.geometry.Vector3D;
import org.ce.domain.model.data.AllClusterData;
import org.ce.infrastructure.logging.LoggingConfig;
import java.util.List;
import java.util.logging.Logger;

/**
 * Single-entry-point orchestrator for the three-stage CVM identification pipeline.
 *
 * <p>This class implements the complete CVM workflow:
 * <ol>
 *   <li><strong>Stage 1: Cluster Identification</strong> â€" Determines distinct cluster
 *       types and their properties (multiplicities, Kikuchi-Baker coefficients) for a
 *       given CVM approximation and pair of phases (disordered reference and ordered
 *       target).</li>
 *   <li><strong>Stage 2: Correlation Function Identification</strong> â€" Enumerates
 *       and groups all distinct correlation functions (decorated clusters) for a
 *       specified number of components.</li>
 *   <li><strong>Stage 3: C-matrix Construction</strong> â€" Builds the C-matrix that
 *       transforms correlation functions to cluster variables.</li>
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
 * // Run pipeline (all 3 stages in one call)
 * AllClusterData allData = CVMPipeline.identify(config);
 *
 * // Access results
 * ClusterIdentificationResult stage1 = allData.getStage1();
 * CFIdentificationResult stage2 = allData.getStage2();
 * CMatrixResult stage3 = allData.getStage3();
 * }</pre>
 *
 * @author  CVM Project
 * @version 2.0
 * @see     CVMConfiguration
 * @see     AllClusterData
 * @see     ClusterIdentifier
 * @see     CFIdentifier
 * @see     CMatrixBuilder
 */
public class CVMPipeline {

    private static final Logger LOG = LoggingConfig.getLogger(CVMPipeline.class);

    private CVMPipeline() {
        // Utility class: prevent instantiation
    }

    /**
     * Runs the complete three-stage CVM identification pipeline.
     *
     * <p>This method:
     * <ol>
     *   <li>Loads cluster and symmetry data from files specified in {@code config}</li>
     *   <li>Executes Stage 1 (ClusterIdentifier) to determine cluster types and properties</li>
     *   <li>Executes Stage 2 (CFIdentifier) to enumerate correlation functions</li>
     *   <li>Executes Stage 3 (CMatrixBuilder) to construct the C-matrix</li>
     *   <li>Wraps all results in a unified {@link AllClusterData}</li>
     * </ol></p>
     *
     * @param config the configuration object specifying files, symmetries, and parameters
     * @return a unified {@link AllClusterData} containing all three stage outputs
     * @throws IllegalArgumentException if configuration is invalid or files cannot be loaded
     * @throws RuntimeException if any stage fails
     */
    public static AllClusterData identify(CVMConfiguration config) {
        long startTime = System.currentTimeMillis();

        if (config == null) {
            throw new IllegalArgumentException("config must not be null");
        }

        LOG.fine("CVMPipeline.identify — ENTER: numComponents=" + config.getNumComponents());

        // =====================================================================
        // Load inputs from configuration (ONCE - no duplicate parsing)
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
        // Stage 3: C-matrix Construction
        // =====================================================================
        CMatrixResult stage3Result = CMatrixBuilder.build(
                stage1Result,
                stage2Result,
                ordMaxClus,
                numComp);

        // =====================================================================
        // Wrap and return unified result
        // =====================================================================
        long computationTime = System.currentTimeMillis() - startTime;
        LOG.fine("CVMPipeline.identify — EXIT: tcdis=" + stage1Result.getTcdis()
                + ", tc=" + stage1Result.getTc()
                + ", tcf=" + stage2Result.getTcf()
                + ", ncf=" + stage2Result.getNcf()
                + ", elapsed=" + computationTime + " ms");

        return new AllClusterData(
                null,  // systemId not available from config alone
                numComp,
                stage1Result,
                stage2Result,
                stage3Result,
                computationTime);
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
            "    .translationVector(new org.ce.domain.identification.geometry.Vector3D(0,0,0))\n" +
            "    .numComponents(2)\n" +
            "    .build();\n" +
            "\n" +
            "// 2. Run pipeline (all 3 stages)\n" +
            "AllClusterData allData = CVMPipeline.identify(config);\n" +
            "\n" +
            "// 3. Access results\n" +
            "ClusterIdentificationResult stage1 = allData.getStage1();\n" +
            "CFIdentificationResult stage2 = allData.getStage2();\n" +
            "CMatrixResult stage3 = allData.getStage3();\n" +
            "\n" +
            "=========================================================\n"
        );
    }
}




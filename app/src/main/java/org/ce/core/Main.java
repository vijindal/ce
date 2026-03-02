package org.ce.core;

import org.ce.core.CVMConfiguration;
import org.ce.core.CVMPipeline;
import org.ce.workbench.backend.data.AllClusterData;
import org.ce.identification.geometry.Vector3D;

/**
 * Simplified application entry point using the {@link CVMPipeline} facade.
 *
 * <p>This demonstrates the unified orchestration API for the full three-stage
 * CVM identification pipeline. Configuration is done via {@link CVMConfiguration}
 * with a builder pattern, and the entire pipeline is invoked with a single
 * call to {@link CVMPipeline#identify(CVMConfiguration)}.</p>
 *
 * @author  CVM Project
 * @version 2.0
 * @see     CVMPipeline
 * @see     CVMConfiguration
 * @see     AllClusterData
 */
public class Main {

    public static void main(String[] args) throws Exception {

        System.out.println("================================================");
        System.out.println("  CVM Pipeline - BCC (A2) Binary System");
        System.out.println("================================================");

        // ============================================================
        // Build configuration using fluent builder API
        // ============================================================
        CVMConfiguration config = CVMConfiguration.builder()
                .disorderedClusterFile("cluster/A2-T.txt")
                .orderedClusterFile("cluster/A2-T.txt")
                .disorderedSymmetryGroup("A2-SG")
                .orderedSymmetryGroup("A2-SG")
                // Identity transformation (default)
                .transformationMatrix(new double[][] {
                        {1.0, 0.0, 0.0},
                        {0.0, 1.0, 0.0},
                        {0.0, 0.0, 1.0}
                })
                // Zero translation (default)
                .translationVector(new Vector3D(0, 0, 0))
                // Binary system
                .numComponents(5)
                .build();

        System.out.println("\n[Configuration]");
        System.out.println("  Disordered file  : " + config.getDisorderedClusterFile());
        System.out.println("  Ordered file     : " + config.getOrderedClusterFile());
        System.out.println("  Components       : " + config.getNumComponents());

        // ============================================================
        // Execute the unified pipeline (all 3 stages)
        // ============================================================
        System.out.println("\n[Pipeline Execution]");
        AllClusterData allData = CVMPipeline.identify(config);

        // ============================================================
        // Access results via unified interface
        // ============================================================
        System.out.println("\n[Results]");
        System.out.println("  Stage 1 (Clusters): tcdis = " + allData.getTcdis());
        System.out.println("  Stage 2 (CFs):      tcf   = " + allData.getTcf());
        System.out.println("  Stage 3 (C-matrix): built = " + (allData.getStage3() != null));
        System.out.println("  Computation time:   " + allData.getComputationTimeMs() + " ms");

        System.out.println("\n================================================");
        System.out.println("  Pipeline execution complete");
        System.out.println("================================================");
    }
}
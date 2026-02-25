package org.ce.app;

import org.ce.app.CVMConfiguration;
import org.ce.app.CVMPipeline;
import org.ce.app.CVMResult;
import org.ce.identification.engine.Vector3D;

/**
 * Simplified application entry point using the new {@link CVMPipeline} facade.
 *
 * <p>This demonstrates the unified orchestration API for the full two-stage
 * CVM identification pipeline. Configuration is done via {@link CVMConfiguration}
 * with a builder pattern, and the entire pipeline is invoked with a single
 * call to {@link CVMPipeline#identify(CVMConfiguration)}.</p>
 *
 * @author  CVM Project
 * @version 1.0
 * @see     CVMPipeline
 * @see     CVMConfiguration
 * @see     CVMResult
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
        // Execute the unified pipeline
        // ============================================================
        System.out.println("\n[Pipeline Execution]");
        CVMResult result = CVMPipeline.identify(config);

        // ============================================================
        // Access results via unified interface
        // ============================================================
        System.out.println("\n[Results]");
        result.printDebug();

        System.out.println("\n================================================");
        System.out.println("  Pipeline execution complete");
        System.out.println("================================================");
    }
}
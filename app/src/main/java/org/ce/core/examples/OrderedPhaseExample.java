package org.ce.app.examples;

import org.ce.core.CVMConfiguration;
import org.ce.core.CVMPipeline;
import org.ce.core.CVMResult;
import org.ce.identification.engine.Vector3D;

/**
 * Example showing how to compare a disordered A2 reference to an ordered B2 phase.
 */
public class OrderedPhaseExample {
    public static void main(String[] args) throws Exception {
        CVMConfiguration config = CVMConfiguration.builder()
                .disorderedClusterFile("cluster/A2-T.txt")
                .orderedClusterFile("cluster/B2-T.txt")
                .disorderedSymmetryGroup("A2-SG")
                .orderedSymmetryGroup("B2-SG")
                .transformationMatrix(new double[][] {
                        {1, 0, 0}, {0, 1, 0}, {0, 0, 1}
                })
                .translationVector(new Vector3D(0.5, 0.5, 0.5))
                .numComponents(3)
                .build();

        CVMResult result = CVMPipeline.identify(config);
        result.printDebug();
    }
}

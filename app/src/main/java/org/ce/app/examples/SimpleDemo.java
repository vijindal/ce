package org.ce.app.examples;

import org.ce.app.CVMConfiguration;
import org.ce.app.CVMPipeline;
import org.ce.app.CVMResult;
import org.ce.identification.engine.Vector3D;

/**
 * Minimal quick-start example showing the CVMPipeline facade.
 */
public class SimpleDemo {
    public static void main(String[] args) throws Exception {
        CVMConfiguration config = CVMConfiguration.builder()
                .disorderedClusterFile("cluster/A2-T.txt")
                .orderedClusterFile("cluster/A2-T.txt")
                .disorderedSymmetryGroup("A2-SG")
                .orderedSymmetryGroup("A2-SG")
                .translationVector(new Vector3D(0, 0, 0))
                .numComponents(2)
                .build();

        CVMResult result = CVMPipeline.identify(config);
        result.printDebug();
    }
}

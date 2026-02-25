package org.ce.app.examples;

import org.ce.app.CVMConfiguration;
import org.ce.app.CVMPipeline;
import org.ce.app.CVMResult;
import org.ce.identification.engine.Vector3D;

/**
 * Compares CF counts between binary and ternary runs for the same structure.
 */
public class CompareBinaryTernary {
    public static void main(String[] args) throws Exception {
        CVMConfiguration base = CVMConfiguration.builder()
                .disorderedClusterFile("cluster/A2-T.txt")
                .orderedClusterFile("cluster/A2-T.txt")
                .disorderedSymmetryGroup("A2-SG")
                .orderedSymmetryGroup("A2-SG")
                .translationVector(new Vector3D(0, 0, 0))
                .build();

        CVMConfiguration binary = CVMConfiguration.builder()
                .disorderedClusterFile(base.getDisorderedClusterFile())
                .orderedClusterFile(base.getOrderedClusterFile())
                .disorderedSymmetryGroup(base.getDisorderedSymmetryGroup())
                .orderedSymmetryGroup(base.getOrderedSymmetryGroup())
                .transformationMatrix(base.getTransformationMatrix())
                .translationVector(base.getTranslationVector())
                .numComponents(2)
                .build();

        CVMConfiguration ternary = CVMConfiguration.builder()
                .disorderedClusterFile(base.getDisorderedClusterFile())
                .orderedClusterFile(base.getOrderedClusterFile())
                .disorderedSymmetryGroup(base.getDisorderedSymmetryGroup())
                .orderedSymmetryGroup(base.getOrderedSymmetryGroup())
                .transformationMatrix(base.getTransformationMatrix())
                .translationVector(base.getTranslationVector())
                .numComponents(4)
                .build();

        CVMResult rBin = CVMPipeline.identify(binary);
        CVMResult rTer = CVMPipeline.identify(ternary);

        int binTcf = rBin.getCorrelationFunctionIdentification().getTcf();
        int terTcf = rTer.getCorrelationFunctionIdentification().getTcf();

        System.out.println("Binary CF count : " + binTcf);
        System.out.println("Ternary CF count: " + terTcf);
    }
}

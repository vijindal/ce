package org.ce.app.examples;

import org.ce.app.CVMConfiguration;
import org.ce.app.CVMPipeline;
import org.ce.cvm.CMatrixResult;
import org.ce.identification.engine.Vector3D;

import java.util.List;

/**
 * Simple demo: build the C-matrix for A2-T binary system.
 */
public class CMatrixDemo {

    public static void main(String[] args) throws Exception {

        CVMConfiguration config = CVMConfiguration.builder()
                .disorderedClusterFile("cluster/A2-T.txt")
                .orderedClusterFile("cluster/A2-T.txt")
                .disorderedSymmetryGroup("A2-SG")
                .orderedSymmetryGroup("A2-SG")
                .transformationMatrix(new double[][]{
                        {1.0, 0.0, 0.0},
                        {0.0, 1.0, 0.0},
                        {0.0, 0.0, 1.0}
                })
                .translationVector(new Vector3D(0, 0, 0))
                .numComponents(2)
                .build();

        CMatrixResult result = CVMPipeline.buildCMatrix(config);

        System.out.println("[CMatrixDemo] A2-T binary");
        int[][] lcv = result.getLcv();
        List<List<double[][]>> cmat = result.getCmat();
        List<List<int[]>> wcv = result.getWcv();

        System.out.println("  types: " + cmat.size());
        for (int t = 0; t < cmat.size(); t++) {
            System.out.println("  t=" + t + " groups=" + cmat.get(t).size());
            for (int j = 0; j < cmat.get(t).size(); j++) {
                double[][] rows = cmat.get(t).get(j);
                int cols = rows.length == 0 ? 0 : rows[0].length;
                System.out.println("    j=" + j + " lcv=" + lcv[t][j]
                        + " rows=" + rows.length + " cols=" + cols
                        + " wcvCount=" + wcv.get(t).get(j).length);
            }
        }
    }
}

package org.ce.identification.engine;

import org.ce.identification.engine.Cluster;
import org.ce.identification.engine.ClusterType;
import java.util.List;

public class SubclusterMatrixBuilder {
    private static final double TOL = 1e-6;

    public static int[][] buildMatrix(List<ClusterType> types) {

        int n = types.size();
        int[][] M = new int[n][n];

        for (int i = 0; i < n; i++) {
            Cluster ci = types.get(i).getRepresentative();

            for (int j = 0; j < n; j++) {
                Cluster cj = types.get(j).getRepresentative();

                int count = 0;

//                for (Cluster sub : SubClusterGenerator.generateSubclusters(ci)) {
//                    if (sub.isTranslatedEquivalent(cj)) {
//                        count++;
//                    }
//                }

                M[i][j] = count;
            }
        }

        return M;
    }
}

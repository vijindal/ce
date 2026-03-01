package org.ce.identification.engine;

import org.ce.identification.geometry.Cluster;
import org.ce.identification.symmetry.SpaceGroup;

public class NormalizerCalculator {
     private static final double TOL = 1e-6;

    public static int computeNormalizerSize(Cluster cluster, SpaceGroup sg) {

        int count = 0;
        

//        for (SymmetryOperation op : sg.getOperations()) {
//            Cluster transformed = cluster.apply(op);
//            if (cluster.isTranslatedEquivalent(transformed)) {
//                count++;
//            } else {
//            }
//        }

        return count;
    }
}


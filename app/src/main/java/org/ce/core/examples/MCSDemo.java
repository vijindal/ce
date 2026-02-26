package org.ce.app.examples;

import org.ce.input.InputLoader;
import org.ce.identification.engine.ClusCoordListGenerator;
import org.ce.identification.engine.ClusCoordListResult;
import org.ce.identification.engine.Cluster;
import org.ce.mcs.MCSRunner;
import org.ce.mcs.MCResult;

import java.util.List;

/**
 * Small driver to exercise the `org.ce.mcs` Monte Carlo path.
 */
public class MCSDemo {
    public static void main(String[] args) throws Exception {
        // Load maximal clusters and space-group (uses classpath resources)
        List<Cluster> maxClusters = InputLoader.parseClusterFile("cluster/A2-T.txt");
        List<?> symOps = InputLoader.parseSymmetryFile("A2-SG");

        // Generate geometric cluster-coordinate list (HSP / disordered)
        ClusCoordListResult clusterData = ClusCoordListGenerator.generate(maxClusters, (List) symOps);
        System.out.println("Cluster types (tc) = " + clusterData.getTc());

        // Simple ECI vector: set ECI = -1 for the first found pair cluster,
        // zero for all other cluster types.
        double[] eci = new double[clusterData.getTc()];
        // Find first cluster type with size == 2 (pair)
        int pairType = -1;
        for (int t = 0; t < clusterData.getClusCoordList().size(); t++) {
            Cluster c = clusterData.getClusCoordList().get(t);
            if (c.getAllSites().size() == 2) { pairType = t; break; }
        }
        if (pairType >= 0) {
            eci[pairType] = -1.0;
            System.out.println("Setting ECI[" + pairType + "] = -1.0 for first pair cluster");
        } else {
            System.out.println("Warning: no pair cluster found; all ECIs set to 0");
        }
        System.out.println("ECI vector: " + java.util.Arrays.toString(eci));

        // Run a short test MCS (binary example)
        MCResult result = MCSRunner.builder()
                .clusterData(clusterData)
                .eci(eci)
                .numComp(2)
                .T(6.20)
                .compositionBinary(0.5)
                .nEquil(1000)
                .nAvg(4000)
            .L(8)
                .seed(12345)
                .build()
                .run();

        result.printDebug();
        org.ce.mcs.Profiler.report();
    }
}

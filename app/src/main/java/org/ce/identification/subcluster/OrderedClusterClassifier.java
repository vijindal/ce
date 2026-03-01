package org.ce.identification.subcluster;

import org.ce.identification.geometry.Cluster;
import org.ce.identification.geometry.Site;
import org.ce.identification.geometry.Sublattice;
import org.ce.identification.result.ClassifiedClusterResult;
import org.ce.identification.result.ClusCoordListResult;
import org.ce.identification.symmetry.OrbitUtils;

import java.util.ArrayList;
import java.util.List;

public class OrderedClusterClassifier {

    public static ClassifiedClusterResult classify(
            ClusCoordListResult disClusData,
            ClusCoordListResult clusData,
            List<Cluster> transformedClusCoordList) {

        List<List<Cluster>> disClusOrbitList =
                disClusData.getOrbitList();

        List<Cluster> clusCoordList1 =
                clusData.getClusCoordList();

        List<Double> clusMList =
                clusData.getMultiplicities();

        List<List<Cluster>> clusOrbitList =
                clusData.getOrbitList();

        List<List<Integer>> clusRcList =
                clusData.getRcList();

        int tcdis = disClusOrbitList.size();
        int tc = transformedClusCoordList.size();

        List<List<Cluster>> classifiedClusCoordList =
                new ArrayList<>();

        List<List<Double>> classifiedClusMList =
                new ArrayList<>();

        List<List<List<Cluster>>> classifiedClusOrbitList =
                new ArrayList<>();

        List<List<List<Integer>>> classifiedClusRcList =
                new ArrayList<>();

        for (int j = 0; j < tcdis; j++) {

            classifiedClusCoordList.add(new ArrayList<>());
            classifiedClusMList.add(new ArrayList<>());
            classifiedClusOrbitList.add(new ArrayList<>());
            classifiedClusRcList.add(new ArrayList<>());

            for (int i = 0; i < tc; i++) {

                // 🔹 Mathematica equivalent:
                // flattenClusCoord = sortClusCoord[Flatten[clusCoordList[[i]],1]]

                Cluster flattened =
                        flattenCluster(transformedClusCoordList.get(i));

                if (OrbitUtils.isContained(
                        disClusOrbitList.get(j),
                        flattened)) {

                    classifiedClusCoordList.get(j)
                            .add(clusCoordList1.get(i));

                    classifiedClusMList.get(j)
                            .add(clusMList.get(i));

                    classifiedClusOrbitList.get(j)
                            .add(clusOrbitList.get(i));

                    classifiedClusRcList.get(j)
                            .add(clusRcList.get(i));
                }
            }
        }

        return new ClassifiedClusterResult(
                classifiedClusCoordList,
                classifiedClusMList,
                classifiedClusOrbitList,
                classifiedClusRcList
        );
    }

    // =========================================================
    // Equivalent to:
    // sortClusCoord[Flatten[clusCoord,1]]
    // =========================================================
    private static Cluster flattenCluster(Cluster cluster) {

        // 1️⃣ Flatten
        List<Site> flatSites =
                new ArrayList<>(cluster.getAllSites());

        // 2️⃣ Sort (Mathematica sortClusCoord equivalent)
        flatSites.sort(Cluster::compareSites);

        // 3️⃣ Wrap into single sublattice
        List<Sublattice> singleSub = new ArrayList<>();
        singleSub.add(new Sublattice(flatSites));

        return new Cluster(singleSub);
    }
}



package org.ce.identification.engine;

import org.ce.identification.geometry.Cluster;
import org.ce.identification.geometry.Site;
import org.ce.identification.geometry.Sublattice;
import org.ce.identification.result.CFGroupingResult;
import org.ce.identification.result.ClusCoordListResult;
import org.ce.identification.symmetry.OrbitUtils;

import java.util.ArrayList;
import java.util.List;

public class CFGroupingUtils {

    public static CFGroupingResult groupCFData(
            ClusCoordListResult disClusData,
            ClusCoordListResult disCFData,
            ClusCoordListResult ordCFData,
            String binaryReferenceSymbol) {

        List<Cluster> disClusCoordData =
                disClusData.getClusCoordList();

        List<List<Cluster>> disClusOrbitData =
                disClusData.getOrbitList();

        List<Cluster> disCFCoordData =
                disCFData.getClusCoordList();

        // ----------------------------------------------------
        // STEP 1: Remove decorations from disordered CF clusters
        // Replace all symbols with binary reference (e.g. "s1")
        // ----------------------------------------------------

        List<Cluster> undecoratedCFClusters =
                new ArrayList<>();

        for (Cluster decorated : disCFCoordData) {

            List<Sublattice> newSublattices =
                    new ArrayList<>();

            for (Sublattice sub :
                    decorated.getSublattices()) {

                List<Site> newSites =
                        new ArrayList<>();

                for (Site site : sub.getSites()) {

                    newSites.add(
                            new Site(
                                    site.getPosition(),
                                    binaryReferenceSymbol
                            )
                    );
                }

                newSublattices.add(
                        new Sublattice(newSites)
                );
            }

            undecoratedCFClusters.add(
                    new Cluster(newSublattices)
            );
        }

        // ----------------------------------------------------
        // STEP 2: Group CFs under geometric clusters
        // ----------------------------------------------------

        List<List<Cluster>> groupedCFCoordData =
                new ArrayList<>();

        List<List<Double>> groupedCFMData =
                new ArrayList<>();

        List<List<List<Cluster>>> groupedCFOrbitData =
                new ArrayList<>();

        List<List<List<Integer>>> groupedCFRData =
                new ArrayList<>();

        for (int i = 0;
             i < disClusCoordData.size();
             i++) {

            groupedCFCoordData.add(new ArrayList<>());
            groupedCFMData.add(new ArrayList<>());
            groupedCFOrbitData.add(new ArrayList<>());
            groupedCFRData.add(new ArrayList<>());

            for (int j = 0;
                 j < undecoratedCFClusters.size();
                 j++) {

                if (OrbitUtils.isContained(
                        disClusOrbitData.get(i),
                        undecoratedCFClusters.get(j))) {

                    groupedCFCoordData.get(i)
                            .add(ordCFData.getClusCoordList().get(j));

                    groupedCFMData.get(i)
                            .add(ordCFData.getMultiplicities().get(j));

                    groupedCFOrbitData.get(i)
                            .add(ordCFData.getOrbitList().get(j));

                    groupedCFRData.get(i)
                            .add(ordCFData.getRcList().get(j));
                }
            }
        }

        return new CFGroupingResult(
                groupedCFCoordData,
                groupedCFMData,
                groupedCFOrbitData,
                groupedCFRData
        );
    }
}



package org.ce.identification.cluster;

import org.ce.identification.engine.Cluster;
import org.ce.identification.engine.*;
import org.ce.identification.engine.ClusCoordListGenerator;
import org.ce.identification.engine.ClusCoordListResult;
import org.ce.identification.engine.SymmetryOperation;

import java.util.List;

/**
 * Orchestrates Stage 1 (Cluster Identification) of the CVM pipeline.
 *
 * <h2>What this stage does</h2>
 * <p>Cluster Identification determines the distinct cluster types for a given
 * CVM approximation and crystal structure, <em>independently of the number of
 * chemical components</em>.  It answers:
 * <ul>
 *   <li>What clusters exist in the CVM approximation?</li>
 *   <li>What are their multiplicities and Kikuchi-Baker entropy coefficients?</li>
 *   <li>How do ordered-phase clusters map to HSP cluster types?</li>
 * </ul>
 *
 * <h2>Stage 1a — HSP clusters (disordered reference)</h2>
 * <p>The Highest Symmetric Phase (HSP) is the fully disordered limit of the
 * structure (e.g. A2 for B2, A1 for L1₂).  Cluster enumeration on the HSP
 * using a <em>binary</em> basis (one site-operator symbol) gives the canonical
 * cluster types whose multiplicities and Kikuchi-Baker coefficients are
 * structure-independent of composition.  These coefficients are used for
 * <em>any</em> number of components.</p>
 *
 * <p>Specifically:</p>
 * <ol>
 *   <li>Run {@code genClusCoordList[disMaxClusCoord, disSymOpList, basisBin]}
 *       to get all distinct HSP clusters → {@code disClusData}</li>
 *   <li>Build the Nij containment table from the HSP cluster orbits</li>
 *   <li>Compute Kikuchi-Baker coefficients from the recurrence</li>
 * </ol>
 *
 * <h2>Stage 1b — Phase clusters (ordered phase)</h2>
 * <p>The actual ordered phase (e.g. B2, L1₂) has lower symmetry than the HSP.
 * Its clusters must be classified back into the HSP cluster types so that
 * CVM entropy contributions are correctly assigned.  This gives:
 * <ul>
 *   <li>{@code lc[t]} — how many ordered-phase clusters map to HSP type {@code t}</li>
 *   <li>{@code mh[t][j]} — normalized multiplicity of ordered cluster {@code j}
 *       within HSP type {@code t}</li>
 * </ul>
 * </p>
 *
 * <p>Specifically:</p>
 * <ol>
 *   <li>Run {@code genClusCoordList[maxClusCoord, symOpList, basisBin]}
 *       to get distinct ordered-phase clusters → {@code clusData}</li>
 *   <li>Transform ordered coordinates into the HSP frame using
 *       {@code ordToDisordCoord[rotateMat, translateMat, ...]}</li>
 *   <li>Classify transformed clusters into HSP types using
 *       {@code transClusCoordList[disClusData, clusData, ...]}</li>
 *   <li>Compute {@code lc} and {@code mh}</li>
 * </ol>
 *
 * <h2>Identity case (disordered input)</h2>
 * <p>When the phase is the same as the HSP (e.g. computing for A2 itself),
 * pass {@code rotateMat = I₃} and {@code translateMat = 0}.  Use the same
 * symmetry operations and maximal cluster for both dis and phase inputs.</p>
 *
 * <h2>Mathematica call sequence</h2>
 * <pre>
 * basisBin       = genBasisSymbolList[2, siteOpSymbol]          // {s[1]}
 * disClusData    = genClusCoordList[disMaxClusCoord, disSymOps, basisBin]
 * nijTable       = getNijTable[disClusCoordList, mhdis, disClusOrbitList]
 * kbdis          = generateKikuchiBakerCoefficients[mhdis, nijTable]
 *
 * clusData       = genClusCoordList[maxClusCoord, symOps, basisBin]
 * clusCoordList  = ordToDisordCoord[rotateMat, translateMat, clusData[[1]]]
 * ordClusData    = transClusCoordList[disClusData, clusData, clusCoordList]
 * lc             = Map[Length, ordClusData[[1]]]
 * mh             = ordClusData[[2]] / mhdis
 * </pre>
 *
 * @see NijTableCalculator
 * @see KikuchiBakerCalculator
 * @see ClusterIdentificationResult
 */
public class ClusterIdentifier {

    private ClusterIdentifier() {}

    /**
     * Runs the full two-stage cluster identification.
     *
     * @param disMaxClusCoord  maximal clusters of the HSP (e.g. A2 tetrahedron)
     * @param disSymOps        space-group operations of the HSP
     * @param maxClusCoord     maximal clusters of the ordered phase (e.g. B2)
     * @param symOps           space-group operations of the ordered phase
     * @param rotateMat        3×3 rotation matrix from ordered to HSP frame
     *                         (identity if phase == HSP)
     * @param translateMat     translation vector from ordered to HSP frame
     *                         (zero if phase == HSP)
     * @return fully populated {@link ClusterIdentificationResult}
     */
    public static ClusterIdentificationResult identify(
            List<Cluster>           disMaxClusCoord,
            List<SymmetryOperation> disSymOps,
            List<Cluster>           maxClusCoord,
            List<SymmetryOperation> symOps,
            double[][]              rotateMat,
            double[]                translateMat) {

        // ================================================================
        // STAGE 1a: HSP clusters (binary basis, disordered symmetry)
        // ================================================================
        System.out.println("=== Stage 1a: Identifying HSP clusters ===");

        // 1a-1. Enumerate HSP clusters with binary basis
        // Mathematica: basisBin = genBasisSymbolList[2, s]  → {s[1]}
        // Mathematica: disClusData = genClusCoordList[disMaxClusCoord, disSymOpList, basisBin]
        List<String> basisBin = List.of("s1");

        ClusCoordListResult disClusterData =
                ClusCoordListGenerator.generate(disMaxClusCoord, disSymOps, basisBin);

        // tcdis excludes the empty cluster (last element in Mathematica 1-indexed list)
        // In Java the empty cluster is the last element (smallest size = 0)
        // tcdis = disClusData[[5]] - 1  (Mathematica)
        int tcdis = countNonEmptyClusters(disClusterData);

        System.out.println("  tcdis (excl. empty) = " + tcdis);
        System.out.println("  nxcdis = 1  (one point-cluster type in HSP)");

        // 1a-2. Compute Nij table
        // Only use non-empty clusters (indices 0..tcdis-1 in descending-size order)
        List<Cluster>       disClusList  = disClusterData.getClusCoordList().subList(0, tcdis);
        List<List<Cluster>> disOrbitList = disClusterData.getOrbitList().subList(0, tcdis);

        int[][] nijTable = NijTableCalculator.compute(disClusList, disOrbitList);

        System.out.println("  Nij table computed.");
        NijTableCalculator.printDebug(nijTable);

        // 1a-3. Compute Kikuchi-Baker coefficients
        double[] mhdis = new double[tcdis];
        List<Double> multiplicities = disClusterData.getMultiplicities();
        for (int t = 0; t < tcdis; t++) {
            mhdis[t] = multiplicities.get(t);
        }

        double[] kbCoefficients = KikuchiBakerCalculator.compute(mhdis, nijTable);

        System.out.println("  Kikuchi-Baker coefficients computed.");
        KikuchiBakerCalculator.printDebug(mhdis, kbCoefficients);

        // ================================================================
        // STAGE 1b: Ordered phase clusters (binary basis, phase symmetry)
        // ================================================================
        System.out.println("\n=== Stage 1b: Identifying ordered-phase clusters ===");

        // 1b-1. Enumerate ordered-phase clusters with binary basis
        // Mathematica: clusData = genClusCoordList[maxClusCoord, symOpList, basisBin]
        ClusCoordListResult phaseClusterData =
                ClusCoordListGenerator.generate(maxClusCoord, symOps, basisBin);

        int tc = countNonEmptyClusters(phaseClusterData);
        System.out.println("  tc (excl. empty) = " + tc);

        // 1b-2. Transform ordered cluster coordinates into HSP frame
        // Mathematica: clusCoordList = ordToDisordCoord[rotateMat, translateMat, clusCoordList]
        List<Cluster> transformedClusList =
                OrderedToDisorderedTransformer.transform(
                        rotateMat,
                        translateMat,
                        phaseClusterData.getClusCoordList());

        // 1b-3. Classify ordered clusters into HSP types
        // Mathematica: ordClusData = transClusCoordList[disClusData, clusData, clusCoordList]
        // We pass a ClusCoordListResult wrapping only the non-empty dis clusters for classification
        ClusCoordListResult disClusDataNonEmpty = trimToNonEmpty(disClusterData, tcdis);
        ClusCoordListResult phaseDataNonEmpty   = trimToNonEmpty(phaseClusterData, tc);

        ClassifiedClusterResult ordClusterData =
                OrderedClusterClassifier.classify(
                        disClusDataNonEmpty,
                        phaseDataNonEmpty,
                        transformedClusList.subList(0, tc));

        // 1b-4. Compute lc and mh
        // Mathematica: lc = Map[Length, ordClusCoordList]
        // Mathematica: mh = ordClusMList / mhdis
        int[]    lc = new int[tcdis];
        double[][] mh = new double[tcdis][];

        for (int t = 0; t < tcdis; t++) {
            List<Cluster> ordClusT = ordClusterData.getCoordList().get(t);
            lc[t] = ordClusT.size();

            List<Double> ordMT = ordClusterData.getMultiplicityList().get(t);
            mh[t] = new double[lc[t]];
            for (int j = 0; j < lc[t]; j++) {
                // Mathematica: mh[[t]][[j]] = ordClusMList[[t]][[j]] / mhdis[[t]]
                mh[t][j] = ordMT.get(j) / mhdis[t];
            }
        }

        // nxc = number of ordered point-cluster types
        // = lc[tcdis-1]  (last HSP type is the point cluster)
        int nxcdis = 1;
        int nxc    = lc[tcdis - 1];
        int nc     = tc - nxc;

        System.out.println("  nxc = " + nxc);
        System.out.println("  nc  = " + nc);

        // Print lc summary
        System.out.print("  lc = [");
        for (int t = 0; t < tcdis; t++) System.out.print(lc[t] + (t < tcdis-1 ? ", " : ""));
        System.out.println("]");

        return new ClusterIdentificationResult(
                disClusterData,
                nijTable,
                kbCoefficients,
                phaseClusterData,
                ordClusterData,
                lc,
                mh,
                tcdis,
                nxcdis,
                tc,
                nxc);
    }

    // -------------------------------------------------------------------------
    // Helpers
    // -------------------------------------------------------------------------

    /**
     * Counts cluster types that have at least one site (excludes the empty cluster).
     * The empty cluster always appears last in the sorted list (smallest size = 0).
     */
    private static int countNonEmptyClusters(ClusCoordListResult data) {
        int count = 0;
        for (Cluster c : data.getClusCoordList()) {
            if (!c.getAllSites().isEmpty()) count++;
        }
        return count;
    }

    /**
     * Returns a new {@link ClusCoordListResult} containing only the first
     * {@code nonEmptyCount} entries (the non-empty clusters).
     * This is needed because {@link OrderedClusterClassifier} expects
     * only non-empty clusters in its input.
     */
    private static ClusCoordListResult trimToNonEmpty(
            ClusCoordListResult data, int nonEmptyCount) {

        return new ClusCoordListResult(
                data.getClusCoordList()  .subList(0, nonEmptyCount),
                data.getMultiplicities() .subList(0, nonEmptyCount),
                data.getOrbitList()      .subList(0, nonEmptyCount),
                data.getRcList()         .subList(0, nonEmptyCount),
                nonEmptyCount,
                data.getNumPointSubClusFound());
    }
}

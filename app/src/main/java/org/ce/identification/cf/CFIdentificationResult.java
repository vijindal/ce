package org.ce.identification.cf;

import org.ce.identification.engine.ClassifiedClusterResult;
import org.ce.identification.engine.ClusCoordListResult;
import org.ce.identification.engine.GroupedCFResult;

import java.util.List;

/**
 * Immutable result of the two-stage Correlation Function Identification pipeline.
 *
 * <h2>Conceptual role</h2>
 * <p>CF identification determines <em>which correlation functions (CFs) exist</em>
 * for a given CVM approximation, structure, and number of components.  Unlike
 * cluster identification (Stage 1), this stage <em>depends on the number of
 * chemical components</em> because decorating sites with more species generates
 * more distinct correlation functions per cluster.</p>
 *
 * <h2>Two stages</h2>
 * <ol>
 *   <li><b>Stage 2a — HSP CFs</b> ({@code disCFData})<br>
 *       All distinct decorated clusters of the HSP using the full n-component
 *       basis.  For a ternary system (numComp=3) and A2-T approximation, this
 *       generates more CFs per cluster than the binary case.</li>
 *
 *   <li><b>Stage 2b — Phase CFs</b> ({@code ordCFData}, {@code groupedCFData})<br>
 *       Decorated clusters of the ordered phase, classified into HSP cluster
 *       types and grouped.  The final grouping {@code groupedCFData} assigns
 *       each CF to its parent HSP cluster type, giving the hierarchical structure
 *       needed for the C-matrix computation.</li>
 * </ol>
 *
 * <h2>Key derived quantities</h2>
 * <ul>
 *   <li>{@code lcf[t][j]} — number of CFs for HSP cluster {@code t},
 *       ordered-phase cluster group {@code j}</li>
 *   <li>{@code tcf} — total number of CFs</li>
 *   <li>{@code nxcf} — number of point-cluster CFs (used for normalization)</li>
 *   <li>{@code mcf[t][j]} — multiplicity of each CF group</li>
 *   <li>{@code rcf[t][j]} — per-sublattice site counts for each CF</li>
 * </ul>
 *
 * <h2>Mathematica equivalents</h2>
 * <pre>
 * disCFData    ←→  disCFData  (from genClusCoordList[disMaxClusCoord, disSymOps, basisN])
 * ordCFData    ←→  ordCFData  (from transClusCoordList[disCFData, CFData, CFCoordList])
 * groupedCFData ←→ cfData    (from groupCFData[disClusData, disCFData, ordCFData, ...])
 * lcf          ←→  lcf        (from readLength[cfCoordList])
 * tcf          ←→  tcf        (Σ lcf)
 * nxcf         ←→  nxcf       (Σ lcf[tcdis])
 * mcf          ←→  mcf        (cfData[[2]])
 * rcf          ←→  rcf        (cfData[[4]])
 * tcfdis       ←→  tcfdis     (total HSP CFs)
 * </pre>
 *
 * @see CFIdentifier
 */
public class CFIdentificationResult {

    // ---- Stage 2a outputs ----

    /** HSP CF data (n-component basis, HSP symmetry). */
    private final ClusCoordListResult disCFData;

    /** Total number of distinct HSP CFs (excluding empty). */
    private final int tcfdis;

    // ---- Stage 2b outputs ----

    /** Phase CF data enumerated with n-component basis under phase symmetry. */
    private final ClusCoordListResult phaseCFData;

    /**
     * Phase CFs classified into HSP cluster types.
     * Parallel to {@code ordClusData} from Stage 1b.
     */
    private final ClassifiedClusterResult ordCFData;

    /**
     * CFs grouped by HSP cluster type then by ordered-phase cluster group.
     * This is the primary output consumed by the C-matrix builder.
     * {@code groupedCFData.getCoordData().get(t).get(j)} = list of CFs
     * belonging to HSP cluster type {@code t}, ordered-phase group {@code j}.
     */
    private final GroupedCFResult groupedCFData;

    // ---- Derived quantities ----

    /**
     * Number of CFs per HSP cluster type per ordered-phase group.
     * {@code lcf[t][j] = groupedCFData.getCoordData().get(t).get(j).size()}.
     * Equivalent to Mathematica {@code lcf = readLength[cfCoordList]}.
     */
    private final int[][] lcf;

    /** Total number of CFs: {@code Σ_{t,j} lcf[t][j]}. */
    private final int tcf;

    /** Number of point-cluster CFs: {@code Σ_j lcf[tcdis-1][j]}. */
    private final int nxcf;

    /** Total non-point CFs: {@code tcf - nxcf}. */
    private final int ncf;

    // -------------------------------------------------------------------------
    // Constructor
    // -------------------------------------------------------------------------

    public CFIdentificationResult(
            ClusCoordListResult   disCFData,
            int                   tcfdis,
            ClusCoordListResult   phaseCFData,
            ClassifiedClusterResult ordCFData,
            GroupedCFResult       groupedCFData,
            int[][]               lcf,
            int                   tcf,
            int                   nxcf,
            int                   ncf) {

        this.disCFData    = disCFData;
        this.tcfdis       = tcfdis;
        this.phaseCFData  = phaseCFData;
        this.ordCFData    = ordCFData;
        this.groupedCFData = groupedCFData;
        this.lcf          = lcf;
        this.tcf          = tcf;
        this.nxcf         = nxcf;
        this.ncf          = ncf;
    }

    // -------------------------------------------------------------------------
    // Accessors
    // -------------------------------------------------------------------------

    public ClusCoordListResult   getDisCFData()     { return disCFData; }
    public int                   getTcfdis()         { return tcfdis; }
    public ClusCoordListResult   getPhaseCFData()    { return phaseCFData; }
    public ClassifiedClusterResult getOrdCFData()    { return ordCFData; }
    public GroupedCFResult       getGroupedCFData()  { return groupedCFData; }
    public int[][]               getLcf()            { return lcf; }
    public int                   getTcf()            { return tcf; }
    public int                   getNxcf()           { return nxcf; }
    public int                   getNcf()            { return ncf; }

    // -------------------------------------------------------------------------
    // Debug print
    // -------------------------------------------------------------------------

    /**
     * Prints a structured summary of the CF identification result to stdout.
     */
    public void printDebug() {
        System.out.println("╔══════════════════════════════════════════════════╗");
        System.out.println("║     CORRELATION FUNCTION IDENTIFICATION          ║");
        System.out.println("╚══════════════════════════════════════════════════╝");

        System.out.println("\n── Stage 2a: HSP CFs ──");
        System.out.println("  tcfdis = " + tcfdis + "  (total HSP CFs, excl. empty)");

        System.out.println("\n── Stage 2b: Grouped CFs (lcf table) ──");
        System.out.println("  tcf  = " + tcf);
        System.out.println("  nxcf = " + nxcf);
        System.out.println("  ncf  = " + ncf);

        System.out.println("\n  lcf[t][j]  (CFs per HSP cluster t, ordered group j):");
        for (int t = 0; t < lcf.length; t++) {
            System.out.printf("  t=%-3d  lcf=", t);
            for (int j = 0; j < lcf[t].length; j++) {
                System.out.printf("%3d ", lcf[t][j]);
            }
            System.out.println();
        }

        System.out.println("\n  mcf[t][j]  (multiplicities):");
        List<List<List<Double>>> mcf = groupedCFData.getMultiplicityData();
        for (int t = 0; t < mcf.size(); t++) {
            System.out.printf("  t=%-3d  mcf=", t);
            for (int j = 0; j < mcf.get(t).size(); j++) {
                System.out.printf("%s ", mcf.get(t).get(j));
            }
            System.out.println();
        }

        System.out.println("\n  rcf[t][j]  (per-sublattice site counts):");
        List<List<List<List<Integer>>>> rcf = groupedCFData.getRcData();
        for (int t = 0; t < rcf.size(); t++) {
            System.out.printf("  t=%-3d  rcf=", t);
            for (int j = 0; j < rcf.get(t).size(); j++) {
                System.out.printf("%s ", rcf.get(t).get(j));
            }
            System.out.println();
        }

        System.out.println("══════════════════════════════════════════════════");
    }
}

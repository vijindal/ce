package org.ce.mcs;

import org.ce.identification.engine.Cluster;
import org.ce.identification.engine.Site;
import org.ce.identification.engine.ClusCoordListResult;
import org.ce.identification.engine.Vector3D;

import java.util.*;

/**
 * Generates all embeddings of abstract cluster types onto the lattice sites
 * of a concrete periodic supercell.
 *
 * <h2>Algorithm</h2>
 * <ol>
 *   <li>Build position→index lookup map (mod-L PBC).</li>
 *   <li>For every orbit member of every cluster type, build n templates
 *       (one per site acting as anchor), where n = cluster size.  This
 *       covers all bond directions including those missing from the orbit's
 *       "positive half-space" representation.</li>
 *   <li>For each supercell site i, try all templates with i as anchor.
 *       Collect raw instances (may include duplicates).</li>
 *   <li>Deduplicate by sorted site-index set within each cluster type.
 *       After deduplication, siteToEmbeddings[i] contains exactly
 *       orbitSize unique instances per cluster type.</li>
 * </ol>
 *
 * @author  CVM Project
 * @version 1.0
 */
public class EmbeddingGenerator {

    private EmbeddingGenerator() {}

    // -------------------------------------------------------------------------
    // Public API
    // -------------------------------------------------------------------------

    /**
     * Generates all embeddings for the given supercell and cluster data.
     *
     * @param latticePositions supercell site positions in unit-cell fractional
     *                         coordinates (NOT divided by L)
     * @param clusterData      cluster types and orbits
     * @param L                supercell repetition factor (PBC wraps in [0, L))
     * @return {@link EmbeddingData} with exactly orbitSize unique instances
     *         per cluster type at every site
     */
    public static EmbeddingData generateEmbeddings(
            List<Vector3D>      latticePositions,
            ClusCoordListResult clusterData,
            int                 L) {

        long __p = Profiler.tic("EmbeddingGenerator.generateEmbeddings");

        int N = latticePositions.size();

        // --------------------------------------------------------
        // Build position → index lookup (mod-L reduced)
        // --------------------------------------------------------
        Map<Vector3DKey, Integer> posToIndex = new HashMap<>();
        for (int i = 0; i < N; i++) {
            posToIndex.put(
                new Vector3DKey(reduceMod(latticePositions.get(i), L)), i);
        }

        // --------------------------------------------------------
        // Build templates: n per orbit member (one per anchor choice)
        // --------------------------------------------------------
        List<ClusterTemplate> templates = buildTemplates(clusterData);

        List<Embedding>   allEmbeddings    = new ArrayList<>();
        @SuppressWarnings("unchecked")
        List<Embedding>[] siteToEmbeddings = new ArrayList[N];
        for (int i = 0; i < N; i++) siteToEmbeddings[i] = new ArrayList<>();

        // --------------------------------------------------------
        // Main loop: anchor site i × template
        // --------------------------------------------------------
        for (int i = 0; i < N; i++) {

            Vector3D anchor = latticePositions.get(i);

            // Step 1: collect all raw instances at site i
            // (includes duplicates from multiple anchor choices of
            // the same physical cluster)
            List<Embedding> raw = new ArrayList<>();

            for (ClusterTemplate template : templates) {

                Vector3D[] rel     = template.getRelativeVectors();
                int[]      indices = new int[rel.length];
                boolean    valid   = true;

                for (int k = 0; k < rel.length; k++) {
                    // rel[0] = (0,0,0) so indices[0] always = i
                    Vector3D target = reduceMod(anchor.add(rel[k]), L);
                    Integer  j      = posToIndex.get(new Vector3DKey(target));
                    if (j == null) { valid = false; break; }
                    indices[k] = j;
                }

                if (!valid) continue;

                int ttype = template.getClusterType();
                int omIdx = template.getOrbitMemberIndex();
                List<org.ce.identification.engine.Cluster> orbit = clusterData.getOrbitList().get(ttype);
                List<org.ce.identification.engine.Site> sites = orbit.get(omIdx).getAllSites();
                int anchorIdx = template.getAnchorIndex();
                int[] alphas = new int[sites.size()];
                alphas[0] = SiteOperatorBasis.alphaFromSymbol(sites.get(anchorIdx).getSymbol());
                int slot = 1;
                for (int k = 0; k < sites.size(); k++) {
                    if (k == anchorIdx) continue;
                    alphas[slot++] = SiteOperatorBasis.alphaFromSymbol(sites.get(k).getSymbol());
                }

                raw.add(new Embedding(
                        template.getClusterType(),
                        template.getOrbitMemberIndex(),
                        indices,
                        alphas));
            }

            // Step 2: deduplicate
            // Two embeddings are the same physical cluster if they have
            // the same cluster type and the same set of site indices
            // (order-independent).  Key = "type:sorted_indices".
            //
            // The first embedding kept for each key preserves the orbit-
            // member index of whichever anchor choice was tried first —
            // this is fine for CF evaluation (we only need the site list).
            //
            // After deduplication, siteToEmbeddings[i] has exactly
            // orbitSize entries per cluster type.
            Set<String>     seen   = new LinkedHashSet<>();
            List<Embedding> deduped = new ArrayList<>();

            for (Embedding e : raw) {
                int[] sorted = e.getSiteIndices().clone();
                Arrays.sort(sorted);
                String key = e.getClusterType() + ":" + Arrays.toString(sorted);
                if (seen.add(key)) {
                    deduped.add(e);
                }
            }

            siteToEmbeddings[i] = deduped;
            allEmbeddings.addAll(deduped);
        }

        Profiler.toc("EmbeddingGenerator.generateEmbeddings", __p);
        
        return new EmbeddingData(allEmbeddings, siteToEmbeddings);
        
        // end
        // Note: cannot place toc after return; so report before returning
    }


    // -------------------------------------------------------------------------
    // Private helpers
    // -------------------------------------------------------------------------

    /**
     * Builds n templates per orbit member (one per anchor-site choice).
     *
     * <p>The orbit stores bonds in only one "half" of direction space.
     * By cycling through all n sites as anchor we recover all directions.
     * The anchor's displacement is always placed at {@code rel[0] = (0,0,0)}
     * so the main-loop condition {@code indices[0] == i} correctly identifies
     * the anchor in the supercell.</p>
     */
    private static List<ClusterTemplate> buildTemplates(
            ClusCoordListResult clusterData) {

        List<ClusterTemplate> templates = new ArrayList<>();
        List<List<Cluster>>   orbitList = clusterData.getOrbitList();

        for (int t = 0; t < orbitList.size(); t++) {
            List<Cluster> orbit = orbitList.get(t);

            for (int o = 0; o < orbit.size(); o++) {
                List<Site> sites = orbit.get(o).getAllSites();
                if (sites.isEmpty()) continue;

                int n = sites.size();

                for (int anchor = 0; anchor < n; anchor++) {

                    Vector3D anchorPos = sites.get(anchor).getPosition();
                    Vector3D[] rel     = new Vector3D[n];

                    // Anchor always goes to slot 0
                    rel[0] = new Vector3D(0, 0, 0);
                    int slot = 1;
                    for (int k = 0; k < n; k++) {
                        if (k == anchor) continue;
                        rel[slot++] = sites.get(k).getPosition().subtract(anchorPos);
                    }

                    templates.add(new ClusterTemplate(t, o, rel, anchor));
                }
            }
        }

        return templates;
    }

    /**
     * Reduces all components of {@code v} modulo {@code L} into {@code [0, L)}.
     */
    static Vector3D reduceMod(Vector3D v, double L) {
        return new Vector3D(
                v.getX() - L * Math.floor(v.getX() / L),
                v.getY() - L * Math.floor(v.getY() / L),
                v.getZ() - L * Math.floor(v.getZ() / L));
    }

    // -------------------------------------------------------------------------
    // Debug
    // -------------------------------------------------------------------------

    /**
     * Prints a debug summary of generator inputs and output.
     */
    public static void printDebug(
            List<Vector3D>      latticePositions,
            ClusCoordListResult clusterData,
            int                 L,
            EmbeddingData       result) {

        System.out.println("==============================");
        System.out.println("[EmbeddingGenerator] DEBUG");
        System.out.println("==============================");
        System.out.println("  INPUT:");
        System.out.println("    supercell L      : " + L);
        System.out.println("    lattice sites    : " + latticePositions.size());
        System.out.println("    cluster types    : " + clusterData.getTc());

        int totalTemplates = 0;
        for (int t = 0; t < clusterData.getOrbitList().size(); t++) {
            int os = clusterData.getOrbitList().get(t).size();
            int cs = clusterData.getClusCoordList().get(t).getAllSites().size();
            totalTemplates += os * cs;
        }
        System.out.println("    total templates  : " + totalTemplates
                + "  (orbitSize × clusterSize per type)");

        System.out.println("  EXPECTED (after deduplication):");
        System.out.printf("    %-16s %-12s %-14s %-14s%n",
                "ClusterType", "orbitSize", "clusterSize", "instances/site");
        for (int t = 0; t < clusterData.getOrbitList().size(); t++) {
            int os = clusterData.getOrbitList().get(t).size();
            int cs = clusterData.getClusCoordList().get(t).getAllSites().size();
            System.out.printf("    ClusterType[%2d]  %-12d %-14d %d%n",
                    t, os, cs, os);
        }

        System.out.println("  OUTPUT:");
        result.printDebug();
        System.out.println("==============================");
    }
}

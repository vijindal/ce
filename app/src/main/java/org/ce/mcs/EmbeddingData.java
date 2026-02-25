package org.ce.mcs;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Container for all {@link Embedding} instances generated for a supercell.
 *
 * <p>{@code EmbeddingData} is the primary output of {@link EmbeddingGenerator}.
 * It provides three complementary views of the same set of embeddings:</p>
 * <ul>
 *   <li><b>All embeddings</b> ({@link #getAllEmbeddings()}) — a flat list of
 *       every cluster instance in the supercell.</li>
 *   <li><b>Site-indexed map</b> ({@link #getSiteToEmbeddings()}) — for each
 *       lattice site {@code i}, every embedding that contains site {@code i}.
 *       This is the key structure for Monte Carlo spin-flip energy updates.</li>
 *   <li><b>Type + site query</b> ({@link #getEmbeddingsForTypeAtSite(int, int)})
 *       — all embeddings of a specific cluster type that touch a specific site,
 *       each annotated with its orbit-member index.</li>
 * </ul>
 *
 * @author  CVM Project
 * @version 1.0
 * @see     EmbeddingGenerator
 * @see     Embedding
 */
public class EmbeddingData {

    /** Flat list of all cluster embeddings in the supercell. */
    private final List<Embedding> allEmbeddings;

    /**
     * Site-to-embedding index: {@code siteToEmbeddings[i]} is the list of
     * all embeddings that contain lattice site {@code i}.
     */
    private final List<Embedding>[] siteToEmbeddings;

    /**
     * Constructs an {@code EmbeddingData} from pre-computed embedding lists.
     *
     * @param allEmbeddings    flat list of all embeddings; must not be {@code null}
     * @param siteToEmbeddings per-site embedding lists; must not be {@code null};
     *                         length must equal the number of lattice sites
     */
    public EmbeddingData(List<Embedding>   allEmbeddings,
                         List<Embedding>[] siteToEmbeddings) {
        this.allEmbeddings    = allEmbeddings;
        this.siteToEmbeddings = siteToEmbeddings;
    }

    // -------------------------------------------------------------------------
    // Accessors
    // -------------------------------------------------------------------------

    /**
     * Returns the flat list of every cluster embedding in the supercell.
     *
     * @return list of all embeddings; never {@code null}
     */
    public List<Embedding> getAllEmbeddings() { return allEmbeddings; }

    /**
     * Returns the per-site embedding index.
     *
     * <p>{@code getSiteToEmbeddings()[i]} gives all embeddings touching site
     * {@code i}. Array length equals the number of lattice sites.</p>
     *
     * @return array of embedding lists; never {@code null}
     */
    public List<Embedding>[] getSiteToEmbeddings() { return siteToEmbeddings; }

    /**
     * Returns the total number of embeddings across the whole supercell.
     *
     * @return {@code allEmbeddings.size()} ≥ 0
     */
    public int totalEmbeddingCount() { return allEmbeddings.size(); }

    /**
     * Returns the number of lattice sites (supercell size).
     *
     * @return {@code siteToEmbeddings.length} ≥ 0
     */
    public int siteCount() { return siteToEmbeddings.length; }

    // -------------------------------------------------------------------------
    // Targeted query
    // -------------------------------------------------------------------------

    /**
     * Returns all embeddings of a specific cluster type that contain a specific
     * lattice site.
     *
     * <p>This is the primary query for the orbit-based analysis: given a cluster
     * type (e.g. 2 = nearest-neighbour pair) and a site index, it returns every
     * realisation of that cluster type in the supercell that touches that site.
     * Each returned {@link Embedding} carries its {@link Embedding#getOrbitMemberIndex()
     * orbitMemberIndex} so the caller knows which symmetry variant each instance is.</p>
     *
     * <p>Example usage:</p>
     * <pre>{@code
     * // All pair (ClusterType 2) clusters touching site 0
     * List<Embedding> pairs = embeddingData.getEmbeddingsForTypeAtSite(2, 0);
     * for (Embedding e : pairs) {
     *     System.out.println("orbit member " + e.getOrbitMemberIndex()
     *             + " → sites " + Arrays.toString(e.getSiteIndices()));
     * }
     * }</pre>
     *
     * @param clusterType the cluster-type index to filter by (0-based)
     * @param siteIndex   the lattice-site index to filter by (0-based)
     * @return new list of matching embeddings; empty if none; never {@code null}
     * @throws IndexOutOfBoundsException if {@code siteIndex} is out of range
     */
    public List<Embedding> getEmbeddingsForTypeAtSite(int clusterType, int siteIndex) {
        List<Embedding> result = new ArrayList<>();
        for (Embedding e : siteToEmbeddings[siteIndex]) {
            if (e.getClusterType() == clusterType) {
                result.add(e);
            }
        }
        return result;
    }

    // -------------------------------------------------------------------------
    // Debug
    // -------------------------------------------------------------------------

    /**
     * Prints a structured debug summary of this embedding data to standard output.
     *
     * <p>Output format:</p>
     * <pre>
     * [EmbeddingData]
     *   total embeddings : 384
     *   supercell sites  : 128
     *   per cluster-type breakdown:
     *     type 0 : 128 embeddings
     *     type 2 : 512 embeddings   (orbit-based: more than before)
     *   site[0] embeddings (N total):
     *     Embedding{type=0, orbit=0, sites=[0]}
     *     Embedding{type=2, orbit=3, sites=[0, 5]}
     *     ...
     * </pre>
     *
     * @param maxSitesToShow maximum number of sites to print in detail
     *                       ({@code 0} skips per-site output)
     */
    public void printDebug(int maxSitesToShow) {
        System.out.println("[EmbeddingData]");
        System.out.println("  total embeddings : " + allEmbeddings.size());
        System.out.println("  supercell sites  : " + siteToEmbeddings.length);

        // Per-cluster-type breakdown
        Map<Integer, Integer> typeCount = new LinkedHashMap<>();
        for (Embedding e : allEmbeddings) {
            typeCount.merge(e.getClusterType(), 1, Integer::sum);
        }
        System.out.println("  per cluster-type breakdown:");
        typeCount.forEach((type, count) ->
                System.out.printf("    type %d : %d embeddings%n", type, count));

        // Per-site detail (limited)
        if (maxSitesToShow > 0) {
            int limit = Math.min(maxSitesToShow, siteToEmbeddings.length);
            for (int i = 0; i < limit; i++) {
                List<Embedding> list = siteToEmbeddings[i];
                System.out.printf("  site[%d] embeddings (%d total):%n", i, list.size());
                for (Embedding e : list) {
                    System.out.println("    " + e);
                }
            }
        }
    }

    /**
     * Prints a targeted debug report for all embeddings of a given cluster type
     * at a given site, showing the orbit-member index and concrete site indices
     * for each instance.
     *
     * <p>Output format:</p>
     * <pre>
     * [EmbeddingData] ClusterType[2] clusters touching site 0
     *   count : 8
     *   [0] orbit member 0 → sites [0, 7]
     *   [1] orbit member 1 → sites [0, 9]
     *   ...
     * </pre>
     *
     * @param clusterType the cluster-type index to report on
     * @param siteIndex   the lattice-site index to report on
     */
    public void printEmbeddingsForTypeAtSite(int clusterType, int siteIndex) {
        List<Embedding> matches = getEmbeddingsForTypeAtSite(clusterType, siteIndex);

        System.out.printf("[EmbeddingData] ClusterType[%d] clusters touching site %d%n",
                clusterType, siteIndex);
        System.out.println("  count : " + matches.size());

        for (int i = 0; i < matches.size(); i++) {
            Embedding e = matches.get(i);
            System.out.printf("  [%d] orbit member %d → sites %s%n",
                    i,
                    e.getOrbitMemberIndex(),
                    java.util.Arrays.toString(e.getSiteIndices()));
        }
    }

    /**
     * Convenience overload of {@link #printDebug(int)} showing the first 2 sites.
     */
    public void printDebug() {
        printDebug(2);
    }
}

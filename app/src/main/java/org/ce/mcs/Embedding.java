package org.ce.mcs;

import java.util.Arrays;

/**
 * Represents a single embedding of an abstract cluster type onto specific
 * lattice sites in a concrete supercell.
 *
 * <p>An {@code Embedding} answers the question: "for cluster type {@code t},
 * orbit member {@code o}, which exact lattice-site indices form one instance
 * of that cluster in the supercell?"</p>
 *
 * <p>The addition of {@link #orbitMemberIndex} over the previous design is
 * critical: it records <em>which</em> symmetry-equivalent variant of the
 * cluster type is realised at these sites.  This allows callers to answer
 * "give me all pair clusters of orientation {@code o} touching site {@code i}"
 * without re-doing any geometry.</p>
 *
 * @author  CVM Project
 * @version 1.0
 * @see     EmbeddingGenerator
 * @see     EmbeddingData
 */
public class Embedding {

    /**
     * Zero-based index into {@link org.ce.identification.engine.ClusCoordListResult#getClusCoordList()},
     * identifying the abstract cluster type (e.g. 2 = nearest-neighbour pair).
     */
    private final int clusterType;

    /**
     * Zero-based index into the orbit list of {@code clusterType}, identifying
     * which symmetry-equivalent variant (orbit member) of the cluster type this
     * embedding realises.
     *
     * <p>For example, if ClusterType[2] has 8 orbit members (8 distinct pair
     * orientations), {@code orbitMemberIndex} in [0..7] says which orientation
     * this particular embedding corresponds to.</p>
     */
    private final int orbitMemberIndex;

    /**
     * Ordered array of lattice-site indices (0-based) that form this embedding.
     * The ordering matches the site ordering of the corresponding
     * {@link ClusterTemplate}.
     */
    private final int[] siteIndices;

    /**
     * Precomputed basis-function alpha indices for each site in this embedding.
     * If non-null, stores one index per site in site-order.
     */
    private final int[] alphaIndices;

    /**
     * Constructs an embedding with precomputed alphas.
     *
     * @param clusterType      zero-based cluster-type index
     * @param orbitMemberIndex zero-based orbit-member index within that cluster type
     * @param siteIndices      lattice-site indices; must not be {@code null}
     * @param alphaIndices     precomputed alpha indices (may be null)
     */
    public Embedding(int clusterType, int orbitMemberIndex, int[] siteIndices, int[] alphaIndices) {
        this.clusterType      = clusterType;
        this.orbitMemberIndex = orbitMemberIndex;
        this.siteIndices      = siteIndices;
        this.alphaIndices     = alphaIndices;
    }

    /**
     * Constructs an embedding for the given cluster type, orbit member, and sites.
     *
     * @param clusterType      zero-based cluster-type index
     * @param orbitMemberIndex zero-based orbit-member index within that cluster type
     * @param siteIndices      lattice-site indices; must not be {@code null}
     */
    public Embedding(int clusterType, int orbitMemberIndex, int[] siteIndices) {
        this(clusterType, orbitMemberIndex, siteIndices, null);
    }

    // -------------------------------------------------------------------------
    // Accessors
    // -------------------------------------------------------------------------

    /**
     * Returns the index of the cluster type that this embedding realises.
     *
     * @return cluster-type index ≥ 0
     */
    public int getClusterType() { return clusterType; }

    /**
     * Returns the index of the orbit member (symmetry variant) that this
     * embedding realises within its cluster type.
     *
     * @return orbit-member index ≥ 0
     */
    public int getOrbitMemberIndex() { return orbitMemberIndex; }

    /**
     * Returns the ordered array of lattice-site indices for this embedding.
     *
     * @return site-index array; never {@code null}
     */
    public int[] getSiteIndices() { return siteIndices; }

    /**
     * Returns the number of sites in this embedding (cluster size).
     *
     * @return {@code siteIndices.length} ≥ 1
     */
    public int size() { return siteIndices.length; }

    /**
     * Returns the precomputed alpha indices, or null.
     *
     * @return alpha-index array or null
     */
    public int[] getAlphaIndices() { return alphaIndices; }

    /**
     * Returns a compact string representation.
     *
     * @return e.g. {@code "Embedding{type=2, orbit=3, sites=[0, 5]}"}
     */
    @Override
    public String toString() {
        return "Embedding{type=" + clusterType
             + ", orbit=" + orbitMemberIndex
             + ", sites=" + Arrays.toString(siteIndices) + "}";
    }

    // -------------------------------------------------------------------------
    // Debug
    // -------------------------------------------------------------------------

    /**
     * Prints a structured debug summary of this embedding to standard output.
     *
     * <p>Output format:</p>
     * <pre>
     * [Embedding]
     *   cluster type       : 2
     *   orbit member index : 3
     *   site count         : 2
     *   site indices       : [0, 5]
     * </pre>
     */
    public void printDebug() {
        System.out.println("[Embedding]");
        System.out.println("  cluster type       : " + clusterType);
        System.out.println("  orbit member index : " + orbitMemberIndex);
        System.out.println("  site count         : " + siteIndices.length);
        System.out.println("  site indices       : " + Arrays.toString(siteIndices));
    }
}

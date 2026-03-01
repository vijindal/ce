package org.ce.mcs;

import org.ce.identification.geometry.Vector3D;

/**
 * A lightweight, position-relative representation of one orbit member of a
 * cluster type, used internally by {@link EmbeddingGenerator}.
 *
 * <p>Previously, one {@code ClusterTemplate} was built per cluster type using
 * the representative cluster only.  Now, one template is built per
 * <em>orbit member</em>, so the generator can distinguish which symmetry
 * variant is realised at each set of lattice sites.</p>
 *
 * <p>A {@code ClusterTemplate} stores:</p>
 * <ul>
 *   <li>{@code clusterType} — which abstract cluster type (e.g. 2 = pair)</li>
 *   <li>{@code orbitMemberIndex} — which symmetry variant within that type</li>
 *   <li>{@code relativeVectors} — displacements of every site from the anchor
 *       ({@code relativeVectors[0]} is always {@code (0,0,0)})</li>
 * </ul>
 *
 * @author  CVM Project
 * @version 1.0
 * @see     EmbeddingGenerator
 */
public class ClusterTemplate {

    /**
     * Zero-based index into the cluster-type list of
     * {@link org.ce.identification.result.ClusCoordListResult#getClusCoordList()}.
     */
    private final int clusterType;

    /**
     * Zero-based index into the orbit list for this cluster type,
     * identifying which symmetry-equivalent variant this template encodes.
     */
    private final int orbitMemberIndex;

    /**
     * Anchor site index within the orbit-member site list (0-based).
     */
    private final int anchorIndex;

    /**
     * Relative displacement vectors from the anchor site (index 0).
     * {@code relativeVectors[0]} is always {@code (0, 0, 0)}.
     */
    private final Vector3D[] relativeVectors;

    /**
     * Constructs a cluster template for a specific orbit member.
     *
     * @param clusterType      zero-based cluster-type index
     * @param orbitMemberIndex zero-based orbit-member index within that cluster type
     * @param relativeVectors  displacement vectors from anchor
     * @param anchorIndex      anchor site index within orbit-member list
     */
    public ClusterTemplate(int clusterType, int orbitMemberIndex, Vector3D[] relativeVectors, int anchorIndex) {
        this.clusterType      = clusterType;
        this.orbitMemberIndex = orbitMemberIndex;
        this.relativeVectors  = relativeVectors;
        this.anchorIndex      = anchorIndex;
    }

    // -------------------------------------------------------------------------
    // Accessors
    // -------------------------------------------------------------------------

    /**
     * Returns the cluster-type index this template belongs to.
     *
     * @return cluster-type index ≥ 0
     */
    public int getClusterType() { return clusterType; }

    /**
     * Returns the orbit-member index this template encodes.
     *
     * @return orbit-member index ≥ 0
     */
    public int getOrbitMemberIndex() { return orbitMemberIndex; }

    /**
     * Returns the relative displacement vectors from the anchor site.
     *
     * @return array of length ≥ 1; {@code relativeVectors[0]} is {@code (0,0,0)}
     */
    public Vector3D[] getRelativeVectors() { return relativeVectors; }

    /**
     * Returns the number of sites in this template (cluster size).
     *
     * @return {@code relativeVectors.length} ≥ 1
     */
    public int size() { return relativeVectors.length; }

    /**
     * Returns the anchor site index.
     *
     * @return anchor index (0-based)
     */
    public int getAnchorIndex() { return anchorIndex; }

    /**
     * Returns a compact string representation.
     *
     * @return e.g. {@code "ClusterTemplate{type=2, orbit=3, size=2}"}
     */
    @Override
    public String toString() {
        return "ClusterTemplate{type=" + clusterType
             + ", orbit=" + orbitMemberIndex
             + ", size=" + relativeVectors.length + "}";
    }

    // -------------------------------------------------------------------------
    // Debug
    // -------------------------------------------------------------------------

    /**
     * Prints a structured debug summary of this template to standard output.
     *
     * <p>Output format:</p>
     * <pre>
     * [ClusterTemplate]
     *   cluster type       : 2
     *   orbit member index : 3
     *   site count         : 2
     *   relative vectors:
     *     [0] (0.000000, 0.000000, 0.000000)  ← anchor
     *     [1] (0.500000, -0.500000, 0.500000)
     * </pre>
     */
    public void printDebug() {
        System.out.println("[ClusterTemplate]");
        System.out.println("  cluster type       : " + clusterType);
        System.out.println("  orbit member index : " + orbitMemberIndex);
        System.out.println("  site count         : " + relativeVectors.length);
        System.out.println("  relative vectors:");
        for (int i = 0; i < relativeVectors.length; i++) {
            System.out.printf("    [%d] %s%s%n",
                    i,
                    relativeVectors[i],
                    i == 0 ? "  ← anchor" : "");
        }
    }
}

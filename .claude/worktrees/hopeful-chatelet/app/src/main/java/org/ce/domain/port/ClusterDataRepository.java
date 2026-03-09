package org.ce.domain.port;

import java.util.Optional;

/**
 * Repository interface for accessing cluster topology data.
 *
 * <p>This port abstracts the storage mechanism for cluster identification
 * results (Stages 1-3). Implementations may load from:</p>
 * <ul>
 *   <li>Classpath resources (bundled data)</li>
 *   <li>Local file cache</li>
 *   <li>Remote database</li>
 * </ul>
 *
 * <h2>Usage</h2>
 * <pre>{@code
 * ClusterDataRepository<MyClusterData> repo = ...; // injected
 * String clusterKey = "BCC_A2_T_bin";
 * Optional<MyClusterData> data = repo.load(clusterKey);
 * }</pre>
 *
 * @since 2.0
 */
public interface ClusterDataRepository<TClusterData> {

    /**
     * Loads cluster topology data for the given key.
     *
     * @param clusterKey element-independent key, e.g., {@code "BCC_A2_T_bin"}
     *                   (structure_phase_model_numComp)
     * @return the cluster data if found, empty otherwise
     * @throws Exception if an I/O or parsing error occurs
     */
    Optional<TClusterData> load(String clusterKey) throws Exception;

    /**
     * Saves cluster topology data under the given key.
     *
     * @param data       the data to save (must not be null)
     * @param clusterKey element-independent key
     * @return true if save succeeded
     * @throws Exception if an I/O error occurs
     */
    boolean save(TClusterData data, String clusterKey) throws Exception;

    /**
     * Checks if data exists for the given key without loading it.
     *
     * @param clusterKey element-independent key
     * @return true if data exists
     */
    boolean exists(String clusterKey);

    /**
     * Deletes cached data for the given key.
     *
     * @param clusterKey element-independent key
     * @return true if deletion succeeded or data didn't exist
     * @throws Exception if an I/O error occurs
     */
    boolean delete(String clusterKey) throws Exception;
}


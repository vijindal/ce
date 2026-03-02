package org.ce.domain.port;

import org.ce.workbench.model.SystemIdentity;

import java.util.Collection;
import java.util.Optional;

/**
 * Repository interface for managing thermodynamic system definitions.
 *
 * <p>A "system" in this context defines a specific alloy configuration:</p>
 * <ul>
 *   <li>Components (elements, e.g., ["Nb", "Ti"])</li>
 *   <li>Crystal structure (e.g., "BCC")</li>
 *   <li>Phase (e.g., "A2")</li>
 *   <li>Approximation model (e.g., "T" for tetrahedron)</li>
 * </ul>
 *
 * <h2>Lifecycle</h2>
 * <p>Systems are registered, used for calculations, and can be removed.
 * The repository tracks both identity (immutable) and status (mutable).</p>
 *
 * @since 2.0
 */
public interface SystemRepository {

    /**
     * Registers a new system in the repository.
     *
     * @param system the system to register (must not be null)
     * @throws IllegalArgumentException if system with same ID already exists
     */
    void register(SystemIdentity system);

    /**
     * Retrieves a system by its unique identifier.
     *
     * @param systemId the system identifier
     * @return the system if found, empty otherwise
     */
    Optional<SystemIdentity> findById(String systemId);

    /**
     * Retrieves all registered systems.
     *
     * @return unmodifiable collection of all systems
     */
    Collection<SystemIdentity> findAll();

    /**
     * Removes a system and its associated data.
     *
     * @param systemId the system identifier
     * @return true if system was removed, false if not found
     * @throws Exception if cleanup fails
     */
    boolean remove(String systemId) throws Exception;

    /**
     * Checks whether cluster identification has been computed for a system.
     *
     * @param systemId the system identifier
     * @return true if clusters have been computed
     */
    boolean isClustersComputed(String systemId);

    /**
     * Checks whether correlation functions have been computed for a system.
     *
     * @param systemId the system identifier
     * @return true if CFs have been computed
     */
    boolean isCfsComputed(String systemId);

    /**
     * Updates the cluster computation status for a system.
     *
     * @param systemId the system identifier
     * @param computed true if clusters are computed
     */
    void setClustersComputed(String systemId, boolean computed);

    /**
     * Updates the CF computation status for a system.
     *
     * @param systemId the system identifier
     * @param computed true if CFs are computed
     */
    void setCfsComputed(String systemId, boolean computed);

    /**
     * Persists any pending changes to storage.
     *
     * @throws Exception if persistence fails
     */
    void flush() throws Exception;
}

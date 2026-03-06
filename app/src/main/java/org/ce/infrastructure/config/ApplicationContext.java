package org.ce.infrastructure.config;

import org.ce.application.cvm.CVMCalculationUseCase;
import org.ce.application.mcs.MCSCalculationUseCase;
import org.ce.application.port.CalculationProgressPort;
import org.ce.domain.port.ClusterDataRepository;
import org.ce.domain.port.ECIRepository;
import org.ce.domain.port.SystemRepository;
import org.ce.infrastructure.cvm.CVMEngineAdapter;
import org.ce.infrastructure.mcs.MCSRunnerAdapter;
import org.ce.infrastructure.persistence.ClusterDataRepositoryAdapter;
import org.ce.infrastructure.persistence.ECIRepositoryAdapter;
import org.ce.infrastructure.persistence.SystemRepositoryAdapter;
import org.ce.domain.model.data.AllClusterData;
import org.ce.infrastructure.registry.SystemRegistry;
import org.ce.domain.system.SystemIdentity;

/**
 * Application context for dependency injection and service wiring.
 *
 * <p>This class provides a centralized location for creating and wiring
 * together application components following the dependency injection pattern.
 * It connects domain ports to their infrastructure implementations.</p>
 *
 * <h2>Architecture</h2>
 * <pre>
 *                    ApplicationContext
 *                           â”‚
 *        â”Œâ”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”¼â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”
 *        â–¼                  â–¼                  â–¼
 *   Domain Ports      Use Cases      Infrastructure
 *   (interfaces)     (application)     (adapters)
 *        â”‚                                   â”‚
 *        â””â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€ implements â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”˜
 * </pre>
 *
 * <h2>Usage</h2>
 * <pre>{@code
 * // Create context with required registry
 * ApplicationContext ctx = ApplicationContext.create(systemRegistry);
 *
 * // Get typed components
 * CVMCalculationUseCase cvmUseCase = ctx.cvmCalculationUseCase(progressPort);
 * MCSCalculationUseCase mcsUseCase = ctx.mcsCalculationUseCase(progressPort);
 * }</pre>
 *
 * @author CVM Project
 * @version 1.0
 * @since Phase 4 - Infrastructure Cleanup
 */
public final class ApplicationContext {

    // -------------------------------------------------------------------------
    // Infrastructure components
    // -------------------------------------------------------------------------

    private final SystemRegistry systemRegistry;

    // -------------------------------------------------------------------------
    // Repository adapters (created on demand, cached)
    // -------------------------------------------------------------------------

    private ClusterDataRepository<AllClusterData> clusterDataRepository;
    private ECIRepository eciRepository;
    private SystemRepository<SystemIdentity> systemRepository;

    // -------------------------------------------------------------------------
    // Construction
    // -------------------------------------------------------------------------

    private ApplicationContext(SystemRegistry systemRegistry) {
        this.systemRegistry = systemRegistry;
    }

    /**
     * Creates an application context with the given system registry.
     *
     * <p>Uses:</p>
     * <ul>
     *   <li>{@link ClusterDataRepositoryAdapter} (wraps static AllClusterDataCache)</li>
     *   <li>{@link ECIRepositoryAdapter} (wraps static ECILoader)</li>
     *   <li>{@link SystemRepositoryAdapter} (wraps provided SystemRegistry)</li>
     * </ul>
     *
     * @param systemRegistry the system registry instance
     * @return configured application context
     */
    public static ApplicationContext create(SystemRegistry systemRegistry) {
        return new ApplicationContext(systemRegistry);
    }

    // -------------------------------------------------------------------------
    // Repository Access (Domain Ports)
    // -------------------------------------------------------------------------

    /**
     * Returns the cluster data repository (lazy initialization).
     *
     * <p>Note: Uses static {@link AllClusterDataCache} internally.</p>
     */
    public ClusterDataRepository<AllClusterData> clusterDataRepository() {
        if (clusterDataRepository == null) {
            clusterDataRepository = new ClusterDataRepositoryAdapter();
        }
        return clusterDataRepository;
    }

    /**
     * Returns the ECI repository (lazy initialization).
     *
     * <p>Note: Uses static {@link org.ce.infrastructure.eci.ECILoader} internally.</p>
     */
    public ECIRepository eciRepository() {
        if (eciRepository == null) {
            eciRepository = new ECIRepositoryAdapter();
        }
        return eciRepository;
    }

    /**
     * Returns the system repository (lazy initialization).
     */
    public SystemRepository<SystemIdentity> systemRepository() {
        if (systemRepository == null) {
            systemRepository = new SystemRepositoryAdapter(systemRegistry);
        }
        return systemRepository;
    }

    // -------------------------------------------------------------------------
    // Use Case Factories
    // -------------------------------------------------------------------------

    /**
     * Creates a CVM calculation use case with the given progress port.
     *
     * @param progressPort port for progress reporting (may be null for NO_OP)
     * @return configured use case
     */
    public CVMCalculationUseCase cvmCalculationUseCase(CalculationProgressPort progressPort) {
        return new CVMCalculationUseCase(progressPort, new CVMEngineAdapter());
    }

    /**
     * Creates an MCS calculation use case with the given progress port.
     *
     * @param progressPort port for progress reporting (may be null for NO_OP)
     * @return configured use case
     */
    public MCSCalculationUseCase mcsCalculationUseCase(CalculationProgressPort progressPort) {
        return new MCSCalculationUseCase(progressPort, new MCSRunnerAdapter());
    }

    // -------------------------------------------------------------------------
    // Direct Infrastructure Access (for legacy code migration)
    // -------------------------------------------------------------------------

    /**
     * Returns the underlying system registry.
     *
     * @deprecated Prefer using {@link #systemRepository()} for new code.
     */
    @Deprecated(since = "2.0")
    public SystemRegistry getSystemRegistry() {
        return systemRegistry;
    }
}


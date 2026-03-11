package org.ce.infrastructure.service;

import org.ce.application.port.DataManagementPort;
import org.ce.domain.model.data.AllClusterData;
import org.ce.domain.system.SystemIdentity;
import org.ce.infrastructure.data.ECILoader;
import org.ce.infrastructure.data.SystemDataLoader;
import org.ce.infrastructure.persistence.AllClusterDataCache;
import org.ce.infrastructure.registry.SystemRegistry;

import java.util.Optional;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Implementation of DataManagementPort.
 *
 * This adapter wraps the existing infrastructure components:
 * - AllClusterDataCache for cluster identification results
 * - SystemDataLoader for CEC database access
 * - ECILoader for ECI loading (database path only, no interactive fallback)
 * - SystemRegistry for system metadata
 *
 * Thread-safe: All underlying components are thread-safe.
 */
public class DataManagementAdapter implements DataManagementPort {

    private static final Logger LOG = Logger.getLogger(DataManagementAdapter.class.getName());

    private final SystemRegistry systemRegistry;

    public DataManagementAdapter(SystemRegistry systemRegistry) {
        this.systemRegistry = systemRegistry;
    }

    @Override
    public boolean isClusterDataAvailable(String clusterKey) {
        try {
            return AllClusterDataCache.exists(clusterKey);
        } catch (Exception e) {
            LOG.log(Level.WARNING, "Failed to check cluster data availability for key: " + clusterKey, e);
            return false;
        }
    }

    @Override
    public Optional<AllClusterData> loadClusterData(String clusterKey) {
        try {
            return AllClusterDataCache.load(clusterKey);
        } catch (Exception e) {
            LOG.log(Level.WARNING, "Failed to load cluster data for key: " + clusterKey, e);
            return Optional.empty();
        }
    }

    @Override
    public boolean isCecAvailable(String cecKey) {
        try {
            // Parse cecKey to extract elements, structure, phase, model
            // Format: "elements_structure_phase_model" e.g. "Nb-Ti_BCC_A2_T"
            String[] parts = cecKey.split("_");
            if (parts.length != 4) {
                return false;
            }
            String elements = parts[0];
            String structure = parts[1];
            String phase = parts[2];
            String model = parts[3];

            return SystemDataLoader.cecExists(elements, structure, phase, model);
        } catch (Exception e) {
            LOG.log(Level.WARNING, "Failed to check CEC availability for key: " + cecKey, e);
            return false;
        }
    }

    @Override
    public Optional<double[]> loadECI(String elements, String structure, String phase,
                                       String model, double temperature, int requiredLength) {
        try {
            // Use ECILoader.loadECIFromDatabase only (silent path, no interactive dialog)
            ECILoader.DBLoadResult result = ECILoader.loadECIFromDatabase(
                elements, structure, phase, model, temperature, requiredLength
            );

            if (result.status == ECILoader.DBLoadResult.Status.OK) {
                LOG.fine("Loaded ECI for " + elements + "/" + structure + "/" + phase + "/" + model
                    + " at T=" + temperature + "K, length=" + result.eci.length
                    + (result.temperatureEvaluated ? " (T-evaluated)" : ""));
                return Optional.of(result.eci);
            } else {
                LOG.warning("ECI load failed for " + elements + "/" + structure + "/" + phase + "/" + model
                    + ": " + result.message);
                return Optional.empty();
            }
        } catch (Exception e) {
            LOG.log(Level.WARNING, "Exception loading ECI for " + elements + "/" + structure + "/" + phase + "/" + model, e);
            return Optional.empty();
        }
    }

    @Override
    public SystemIdentity getSystem(String systemId) {
        try {
            return systemRegistry.getSystem(systemId);
        } catch (Exception e) {
            LOG.log(Level.WARNING, "Failed to get system: " + systemId, e);
            return null;
        }
    }

    @Override
    public boolean isCfsComputed(String systemId) {
        try {
            return systemRegistry.isCfsComputed(systemId);
        } catch (Exception e) {
            LOG.log(Level.WARNING, "Failed to check CFs for system: " + systemId, e);
            return false;
        }
    }

    @Override
    public boolean isClustersComputed(String systemId) {
        try {
            return systemRegistry.isClustersComputed(systemId);
        } catch (Exception e) {
            LOG.log(Level.WARNING, "Failed to check clusters for system: " + systemId, e);
            return false;
        }
    }
}

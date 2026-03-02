package org.ce.infrastructure.persistence;

import org.ce.domain.port.ClusterDataRepository;
import org.ce.workbench.backend.data.AllClusterData;
import org.ce.workbench.util.cache.AllClusterDataCache;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Optional;

/**
 * Adapter implementing {@link ClusterDataRepository} by delegating to
 * the static {@link AllClusterDataCache}.
 *
 * <p>This adapter enables dependency injection and testing by providing
 * an instance-based interface to the existing static cache.</p>
 *
 * <h2>Usage</h2>
 * <pre>{@code
 * ClusterDataRepository repo = new ClusterDataRepositoryAdapter();
 * Optional<AllClusterData> data = repo.load("BCC_A2_T_bin");
 * }</pre>
 *
 * @since 2.0
 */
public class ClusterDataRepositoryAdapter implements ClusterDataRepository {

    /**
     * Creates a new adapter instance.
     */
    public ClusterDataRepositoryAdapter() {
        // Stateless adapter - delegates all calls to static AllClusterDataCache
    }

    @Override
    public Optional<AllClusterData> load(String clusterKey) throws Exception {
        return AllClusterDataCache.load(clusterKey);
    }

    @Override
    public boolean save(AllClusterData data, String clusterKey) throws Exception {
        return AllClusterDataCache.save(data, clusterKey);
    }

    @Override
    public boolean exists(String clusterKey) {
        return AllClusterDataCache.exists(clusterKey);
    }

    @Override
    public boolean delete(String clusterKey) throws Exception {
        Path dir = AllClusterDataCache.resolveDir(clusterKey);
        if (!Files.exists(dir)) {
            return true; // Already deleted
        }
        
        // Delete directory recursively
        Files.walk(dir)
             .sorted(java.util.Comparator.reverseOrder())
             .forEach(path -> {
                 try {
                     Files.delete(path);
                 } catch (java.io.IOException e) {
                     System.err.println("Failed to delete: " + path);
                 }
             });
        
        return !Files.exists(dir);
    }
}

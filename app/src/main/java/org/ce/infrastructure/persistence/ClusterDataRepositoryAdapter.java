package org.ce.infrastructure.persistence;

import org.ce.domain.port.ClusterDataRepository;
import org.ce.infrastructure.persistence.migration.ClusterCacheSchemaMigrator;
import org.ce.domain.model.data.AllClusterData;
import org.ce.infrastructure.cache.AllClusterDataCache;
import org.json.JSONObject;

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
public class ClusterDataRepositoryAdapter implements ClusterDataRepository<AllClusterData> {

    private static final String CACHE_FILE = "all_cluster_data.json";

    /**
     * Creates a new adapter instance.
     */
    public ClusterDataRepositoryAdapter() {
        // Stateless adapter - delegates all calls to static AllClusterDataCache
    }

    @Override
    public Optional<AllClusterData> load(String clusterKey) throws Exception {
        Path file = AllClusterDataCache.resolveDir(clusterKey).resolve(CACHE_FILE);
        if (Files.exists(file)) {
            JSONObject root = new JSONObject(Files.readString(file));
            boolean migrated = ClusterCacheSchemaMigrator.migrateRootInMemory(root);
            validateJsonContract(root, clusterKey);
            if (migrated) {
                Files.writeString(file, root.toString(2));
            }
        }

        Optional<AllClusterData> loaded = AllClusterDataCache.load(clusterKey);
        loaded.ifPresent(data -> validateLoadedContract(data, clusterKey));
        return loaded;
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

    private static void validateJsonContract(JSONObject root, String clusterKey) {
        int schemaVersion = root.optInt("schemaVersion", 0);
        if (schemaVersion < ClusterCacheSchemaMigrator.CURRENT_SCHEMA_VERSION) {
            throw new IllegalStateException("Cache schema version too old for key '" + clusterKey
                    + "': " + schemaVersion);
        }

        if (!root.has("stage3")) {
            throw new IllegalStateException("Missing stage3 in cache for key '" + clusterKey + "'");
        }

        JSONObject stage3 = root.getJSONObject("stage3");
        require(stage3, "cmat", clusterKey);
        require(stage3, "lcv", clusterKey);
        require(stage3, "wcv", clusterKey);
        require(stage3, "cfBasisIndices", clusterKey);
    }

    private static void require(JSONObject obj, String field, String clusterKey) {
        if (!obj.has(field)) {
            throw new IllegalStateException("Missing stage3." + field + " for cache key '" + clusterKey + "'");
        }
    }

    private static void validateLoadedContract(AllClusterData data, String clusterKey) {
        if (data.getStage3() == null
                || data.getStage3().getCmat() == null
                || data.getStage3().getLcv() == null
                || data.getStage3().getWcv() == null
                || data.getStage3().getCfBasisIndices() == null) {
            throw new IllegalStateException("Invalid stage3 contract in loaded cache for key '" + clusterKey + "'");
        }
    }
}


package org.ce.infrastructure.registry;

import org.ce.domain.system.SystemIdentity;
import org.ce.domain.system.SystemStatus;
import org.json.JSONArray;
import org.json.JSONObject;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.logging.Logger;
import org.ce.infrastructure.logging.LoggingConfig;

/**
 * Central registry for managing systems and their computation status.
 *
 * <p>Owns both {@link SystemIdentity} (immutable) and {@link SystemStatus}
 * (thread-safe mutable) for each registered system. Status updates must go
 * through this registry to ensure thread safety.</p>
 *
 * <p>Calculation results are managed separately by {@link ResultRepository},
 * following the Single Responsibility Principle.</p>
 *
 * <h2>Persistence Strategy</h2>
 * <p>Uses deferred persistence to avoid synchronous disk I/O on every operation.
 * Changes are tracked via a dirty flag and persisted:</p>
 * <ul>
 *   <li>Explicitly via {@link #persistIfDirty()}</li>
 *   <li>On application shutdown via {@link #shutdown()}</li>
 * </ul>
 */
public class SystemRegistry {

    private static final Logger LOG = LoggingConfig.getLogger(SystemRegistry.class);

    private final Path registryRoot;
    private final Map<String, SystemIdentity> identities;
    private final Map<String, SystemStatus> statuses;
    
    /** Tracks whether in-memory state differs from disk. */
    private final AtomicBoolean dirty = new AtomicBoolean(false);
    
    private static final String CACHE_DIR = "cache";
    private static final String SYSTEMS_FILE = "systems.dat";
    
    public SystemRegistry(Path workspaceRoot) throws IOException {
        this.registryRoot = workspaceRoot.resolve(".ce-workbench");
        this.identities = new ConcurrentHashMap<>();
        this.statuses = new ConcurrentHashMap<>();
        
        // Initialize workspace
        Files.createDirectories(registryRoot);
        Files.createDirectories(registryRoot.resolve(CACHE_DIR));
        
        // Load existing systems
        loadSystemsFromDisk();
    }
    
    // =========================================================================
    // System Identity Management
    // =========================================================================

    /**
     * Register a new system in the registry.
     * 
     * <p>Note: Changes are deferred to disk. Call {@link #persistIfDirty()} or
     * {@link #shutdown()} to persist.</p>
     */
    public void registerSystem(SystemIdentity system) {
        Objects.requireNonNull(system, "system");
        identities.put(system.getId(), system);
        statuses.put(system.getId(), new SystemStatus());
        dirty.set(true);
    }
    
    /**
     * Retrieve a system identity by ID.
     */
    public SystemIdentity getSystem(String systemId) {
        return identities.get(systemId);
    }
    
    /**
     * Get all registered systems.
     */
    public Collection<SystemIdentity> getAllSystems() {
        return new ArrayList<>(identities.values());
    }

    /**
     * Auto-discover and register systems from the filesystem.
     *
     * <p>Scans the systems directory for CEC files and auto-generates SystemIdentity
     * objects. This eliminates the need for manual system registration.</p>
     *
     * @param systemsRoot Root directory containing system subdirectories
     *                    (e.g., {@code app/src/main/resources/data/systems/})
     * @return Number of systems discovered and registered
     */
    public int loadSystemsFromFilesystem(Path systemsRoot) {
        List<SystemIdentity> discovered = SystemDiscovery.discoverSystems(systemsRoot);
        for (SystemIdentity system : discovered) {
            if (identities.containsKey(system.getId())) {
                LOG.fine("System already registered: " + system.getId());
                continue;
            }
            registerSystem(system);
            // Mark as having CEC available (discovered from filesystem)
            markCecAvailable(system.getId(), true);
            LOG.info("Auto-registered system: " + system.getId());
        }
        return discovered.size();
    }

    /**
     * Remove a system and its cached data.
     */
    public void removeSystem(String systemId) throws IOException {
        identities.remove(systemId);
        statuses.remove(systemId);
        
        // Delete cached data
        Path systemCacheDir = registryRoot.resolve(CACHE_DIR).resolve(systemId);
        if (Files.exists(systemCacheDir)) {
            deleteDirectory(systemCacheDir);
        }
        
        dirty.set(true);
    }

    // =========================================================================
    // System Status Management (thread-safe)
    // =========================================================================

    /**
     * Get the status for a system.
     */
    public SystemStatus getStatus(String systemId) {
        return statuses.get(systemId);
    }

    /**
     * Mark clusters as computed for a system.
     */
    public void markClustersComputed(String systemId, boolean computed) {
        SystemStatus status = statuses.get(systemId);
        if (status != null) {
            status.setClustersComputed(computed);
        }
    }

    /**
     * Mark CFs as computed for a system.
     */
    public void markCfsComputed(String systemId, boolean computed) {
        SystemStatus status = statuses.get(systemId);
        if (status != null) {
            status.setCfsComputed(computed);
        }
    }

    /**
     * Mark CEC availability for a system.
     */
    public void markCecAvailable(String systemId, boolean available) {
        SystemStatus status = statuses.get(systemId);
        if (status != null) {
            status.setCecAvailable(available);
        }
    }

    /**
     * Convenience: check if clusters are computed.
     */
    public boolean isClustersComputed(String systemId) {
        SystemStatus status = statuses.get(systemId);
        return status != null && status.isClustersComputed();
    }

    /**
     * Convenience: check if CFs are computed.
     */
    public boolean isCfsComputed(String systemId) {
        SystemStatus status = statuses.get(systemId);
        return status != null && status.isCfsComputed();
    }

    /**
     * Convenience: check if CEC is available.
     */
    public boolean isCecAvailable(String systemId) {
        SystemStatus status = statuses.get(systemId);
        return status != null && status.isCecAvailable();
    }

    // =========================================================================
    // Cache Directory Management
    // =========================================================================
    
    /**
     * Get the cache directory for a specific system.
     */
    public Path getSystemCacheDir(String systemId) throws IOException {
        Path cacheDir = registryRoot.resolve(CACHE_DIR).resolve(systemId);
        Files.createDirectories(cacheDir);
        return cacheDir;
    }
    
    // =========================================================================
    // Persistence
    // =========================================================================

    /**
     * Persist registry to disk if there are unsaved changes.
     * 
     * @return true if data was written, false if already up-to-date
     */
    public boolean persistIfDirty() {
        if (dirty.compareAndSet(true, false)) {
            saveSystemsToDisk();
            return true;
        }
        return false;
    }
    
    /**
     * Shutdown hook: persist any unsaved changes.
     * Call this on application exit.
     */
    public void shutdown() {
        persistIfDirty();
    }
    
    /**
     * Get workspace statistics.
     */
    public RegistryStats getStats() {
        return new RegistryStats(
            identities.size(),
            registryRoot.toFile().getTotalSpace(),
            getDirectorySize(registryRoot)
        );
    }
    
    // ===================== PRIVATE HELPERS =====================
    
    private void loadSystemsFromDisk() {
        Path systemsFile = registryRoot.resolve(SYSTEMS_FILE);
        if (!Files.exists(systemsFile)) {
            return;
        }
        try {
            String json = Files.readString(systemsFile, StandardCharsets.UTF_8);
            JSONArray arr = new JSONArray(json);
            for (int i = 0; i < arr.length(); i++) {
                JSONObject obj = arr.getJSONObject(i);
                String id = obj.getString("id");
                if (identities.containsKey(id)) continue; // don't overwrite already-loaded

                JSONArray compsArr = obj.getJSONArray("components");
                String[] components = new String[compsArr.length()];
                for (int j = 0; j < compsArr.length(); j++) components[j] = compsArr.getString(j);

                SystemIdentity system = SystemIdentity.builder()
                    .id(id)
                    .name(obj.optString("name", id))
                    .structure(obj.optString("structure", ""))
                    .phase(obj.optString("phase", ""))
                    .model(obj.optString("model", ""))
                    .components(components)
                    .clusterFilePath("")
                    .symmetryGroupName("")
                    .build();
                identities.put(id, system);

                SystemStatus st = new SystemStatus();
                st.setCecAvailable(obj.optBoolean("cecAvailable", false));
                st.setClustersComputed(obj.optBoolean("clustersComputed", false));
                st.setCfsComputed(obj.optBoolean("cfsComputed", false));
                statuses.put(id, st);
            }
            LOG.info("Loaded " + arr.length() + " systems from registry file");
        } catch (Exception e) {
            LOG.warning("Failed to load systems: " + e.getMessage());
        }
    }

    private void saveSystemsToDisk() {
        try {
            Path systemsFile = registryRoot.resolve(SYSTEMS_FILE);
            JSONArray arr = new JSONArray();
            for (SystemIdentity s : identities.values()) {
                JSONObject obj = new JSONObject();
                obj.put("id", s.getId());
                obj.put("name", s.getName());
                obj.put("structure", s.getStructure());
                obj.put("phase", s.getPhase());
                obj.put("model", s.getModel());
                JSONArray comps = new JSONArray();
                for (String c : s.getComponents()) comps.put(c);
                obj.put("components", comps);
                SystemStatus st = statuses.get(s.getId());
                if (st != null) {
                    obj.put("cecAvailable", st.isCecAvailable());
                    obj.put("clustersComputed", st.isClustersComputed());
                    obj.put("cfsComputed", st.isCfsComputed());
                }
                arr.put(obj);
            }
            Files.writeString(systemsFile, arr.toString(2), StandardCharsets.UTF_8);
            LOG.fine("Saved " + identities.size() + " systems to registry file");
        } catch (Exception e) {
            LOG.warning("Failed to save systems: " + e.getMessage());
        }
    }
    

    private void deleteDirectory(Path path) throws IOException {
        Files.walk(path)
            .sorted(Comparator.reverseOrder())
            .forEach(p -> {
                try {
                    Files.delete(p);
                } catch (IOException e) {
                    LOG.warning("Failed to delete: " + p);
                }
            });
    }
    
    private long getDirectorySize(Path path) {
        try {
            return Files.walk(path)
                .filter(Files::isRegularFile)
                .mapToLong(p -> {
                    try {
                        return Files.size(p);
                    } catch (IOException e) {
                        return 0;
                    }
                })
                .sum();
        } catch (IOException e) {
            return 0;
        }
    }
    
    // ===================== INNER CLASSES =====================
    
    /**
     * Statistics about the registry.
     */
    public static class RegistryStats {
        public final int systemCount;
        public final long totalSpace;
        public final long usedSpace;
        
        public RegistryStats(int systemCount, long totalSpace, long usedSpace) {
            this.systemCount = systemCount;
            this.totalSpace = totalSpace;
            this.usedSpace = usedSpace;
        }
    }
}


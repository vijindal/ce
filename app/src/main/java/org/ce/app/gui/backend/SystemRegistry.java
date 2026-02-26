package org.ce.app.gui.backend;

import org.ce.app.gui.models.CalculationResults;
import org.ce.app.gui.models.SystemInfo;
import java.io.*;
import java.nio.file.*;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Central registry for managing systems and their cached computation results.
 * Handles system lifecycle, cluster/CF cache management, and persistence.
 */
public class SystemRegistry {
    
    private final Path registryRoot;
    private final Map<String, SystemInfo> systems;
    private final Map<String, CalculationResults> results;
    
    private static final String CACHE_DIR = "cache";
    private static final String RESULTS_DIR = "results";
    private static final String SYSTEMS_FILE = "systems.dat";
    
    public SystemRegistry(Path workspaceRoot) throws IOException {
        this.registryRoot = workspaceRoot.resolve(".ce-workbench");
        this.systems = new ConcurrentHashMap<>();
        this.results = new ConcurrentHashMap<>();
        
        // Initialize workspace
        Files.createDirectories(registryRoot);
        Files.createDirectories(registryRoot.resolve(CACHE_DIR));
        Files.createDirectories(registryRoot.resolve(RESULTS_DIR));
        
        // Load existing systems
        loadSystemsFromDisk();
        scanResultsFromDisk();
    }
    
    /**
     * Register a new system in the registry.
     */
    public void registerSystem(SystemInfo system) {
        Objects.requireNonNull(system, "system");
        systems.put(system.getId(), system);
        saveSystemsToDisk();
    }
    
    /**
     * Retrieve a system by ID.
     */
    public SystemInfo getSystem(String systemId) {
        return systems.get(systemId);
    }
    
    /**
     * Get all registered systems.
     */
    public Collection<SystemInfo> getAllSystems() {
        return new ArrayList<>(systems.values());
    }
    
    /**
     * Remove a system and its cached data.
     */
    public void removeSystem(String systemId) throws IOException {
        systems.remove(systemId);
        
        // Delete cached data
        Path systemCacheDir = registryRoot.resolve(CACHE_DIR).resolve(systemId);
        if (Files.exists(systemCacheDir)) {
            deleteDirectory(systemCacheDir);
        }
        
        // Delete results
        Path systemResultsDir = registryRoot.resolve(RESULTS_DIR).resolve(systemId);
        if (Files.exists(systemResultsDir)) {
            deleteDirectory(systemResultsDir);
        }
        
        // Delete results from memory
        results.entrySet().removeIf(e -> e.getValue().getConfig().getSystem().getId().equals(systemId));
        
        saveSystemsToDisk();
    }
    
    /**
     * Get the cache directory for a specific system.
     */
    public Path getSystemCacheDir(String systemId) throws IOException {
        Path cacheDir = registryRoot.resolve(CACHE_DIR).resolve(systemId);
        Files.createDirectories(cacheDir);
        return cacheDir;
    }
    
    /**
     * Store calculation results.
     */
    public void storeResult(String resultId, CalculationResults results) {
        Objects.requireNonNull(resultId, "resultId");
        Objects.requireNonNull(results, "results");
        
        this.results.put(resultId, results);
        
        // Serialize to disk (JSON format)
        try {
            Path systemId = Paths.get(results.getConfig().getSystem().getId());
            Path resultsDir = registryRoot.resolve(RESULTS_DIR).resolve(systemId);
            Files.createDirectories(resultsDir);
            
            Path resultFile = resultsDir.resolve(resultId + ".json");
            // TODO: Implement JSON serialization
            Files.writeString(resultFile, serializeResult(results));
        } catch (IOException e) {
            System.err.println("Failed to serialize results: " + e.getMessage());
        }
    }
    
    /**
     * Retrieve stored results.
     */
    public CalculationResults getResult(String resultId) {
        return results.get(resultId);
    }
    
    /**
     * Get all results for a specific system.
     */
    public List<CalculationResults> getSystemResults(String systemId) {
        List<CalculationResults> systemResults = new ArrayList<>();
        results.forEach((id, result) -> {
            if (result.getConfig().getSystem().getId().equals(systemId)) {
                systemResults.add(result);
            }
        });
        return systemResults;
    }
    
    /**
     * Get all stored results.
     */
    public Collection<CalculationResults> getAllResults() {
        return new ArrayList<>(results.values());
    }
    
    /**
     * Clear old results (older than specified days).
     */
    public void purgeOldResults(int daysOld) throws IOException {
        long cutoffTime = System.currentTimeMillis() - (daysOld * 24L * 60 * 60 * 1000);
        
        results.entrySet().removeIf(e -> {
            long resultTime = e.getValue().getWallClockTimeMs();
            return resultTime < cutoffTime;
        });
        
        // Also delete from disk
        Path resultsDir = registryRoot.resolve(RESULTS_DIR);
        if (Files.exists(resultsDir)) {
            Files.walk(resultsDir)
                .filter(Files::isRegularFile)
                .filter(p -> p.toString().endsWith(".json"))
                .filter(p -> p.toFile().lastModified() < cutoffTime)
                .forEach(p -> {
                    try {
                        Files.delete(p);
                    } catch (IOException e) {
                        System.err.println("Failed to delete: " + p);
                    }
                });
        }
    }
    
    /**
     * Get workspace statistics.
     */
    public RegistryStats getStats() {
        return new RegistryStats(
            systems.size(),
            results.size(),
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
            // TODO: Implement deserialization from file
            // For now, systems are built at runtime
        } catch (Exception e) {
            System.err.println("Failed to load systems: " + e.getMessage());
        }
    }
    
    private void saveSystemsToDisk() {
        try {
            Path systemsFile = registryRoot.resolve(SYSTEMS_FILE);
            // TODO: Implement serialization to file
        } catch (Exception e) {
            System.err.println("Failed to save systems: " + e.getMessage());
        }
    }
    
    private void scanResultsFromDisk() {
        Path resultsDir = registryRoot.resolve(RESULTS_DIR);
        if (!Files.exists(resultsDir)) {
            return;
        }
        
        try {
            Files.walk(resultsDir)
                .filter(Files::isRegularFile)
                .filter(p -> p.toString().endsWith(".json"))
                .forEach(p -> {
                    try {
                        // TODO: Load and deserialize result
                    } catch (Exception e) {
                        System.err.println("Failed to load result: " + p);
                    }
                });
        } catch (IOException e) {
            System.err.println("Failed to scan results: " + e.getMessage());
        }
    }
    
    private String serializeResult(CalculationResults result) {
        // TODO: Implement proper JSON serialization
        return "{}";
    }
    
    private void deleteDirectory(Path path) throws IOException {
        Files.walk(path)
            .sorted(Comparator.reverseOrder())
            .forEach(p -> {
                try {
                    Files.delete(p);
                } catch (IOException e) {
                    System.err.println("Failed to delete: " + p);
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
        public final int resultCount;
        public final long totalSpace;
        public final long usedSpace;
        
        public RegistryStats(int systemCount, int resultCount, long totalSpace, long usedSpace) {
            this.systemCount = systemCount;
            this.resultCount = resultCount;
            this.totalSpace = totalSpace;
            this.usedSpace = usedSpace;
        }
    }
}

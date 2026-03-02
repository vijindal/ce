package org.ce.workbench.backend.registry;

import org.ce.workbench.backend.data.CalculationMethod;
import org.ce.workbench.backend.data.CalculationRecord;

import java.io.*;
import java.nio.file.*;
import java.time.LocalDateTime;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Predicate;
import java.util.stream.Collectors;

/**
 * Repository for storing and querying calculation results.
 *
 * <p>Separated from {@link SystemRegistry} following the Single Responsibility
 * Principle. This class handles:</p>
 * <ul>
 *   <li>In-memory storage of {@link CalculationRecord} instances</li>
 *   <li>Persistence to/from disk (JSON format)</li>
 *   <li>Querying by system, temperature, composition, date, method, etc.</li>
 *   <li>Result lifecycle management (purging old results)</li>
 * </ul>
 *
 * <p>Thread-safe via ConcurrentHashMap for concurrent access from background jobs.</p>
 *
 * @see CalculationRecord
 */
public class ResultRepository {

    private final Path resultsRoot;
    private final Map<String, CalculationRecord> results;

    /**
     * Creates a result repository at the specified workspace root.
     *
     * @param workspaceRoot root directory for the workspace
     * @throws IOException if directory creation fails
     */
    public ResultRepository(Path workspaceRoot) throws IOException {
        this.resultsRoot = workspaceRoot.resolve(".ce-workbench").resolve("results");
        this.results = new ConcurrentHashMap<>();
        
        Files.createDirectories(resultsRoot);
        loadResultsFromDisk();
    }

    // =========================================================================
    // Storage
    // =========================================================================

    /**
     * Stores a calculation result.
     *
     * @param record the calculation record to store
     */
    public void store(CalculationRecord record) {
        Objects.requireNonNull(record, "record");
        
        results.put(record.id(), record);
        persistResult(record);
    }

    /**
     * Retrieves a result by ID.
     *
     * @param resultId the result identifier
     * @return the record, or null if not found
     */
    public CalculationRecord get(String resultId) {
        return results.get(resultId);
    }

    /**
     * Removes a result by ID.
     *
     * @param resultId the result identifier
     * @return true if the result was removed
     */
    public boolean remove(String resultId) {
        CalculationRecord removed = results.remove(resultId);
        if (removed != null) {
            deleteResultFile(removed);
            return true;
        }
        return false;
    }

    /**
     * Returns all stored results.
     *
     * @return unmodifiable collection of all records
     */
    public Collection<CalculationRecord> getAll() {
        return Collections.unmodifiableCollection(results.values());
    }

    /**
     * Returns the count of stored results.
     *
     * @return number of results
     */
    public int count() {
        return results.size();
    }

    // =========================================================================
    // Querying
    // =========================================================================

    /**
     * Gets all results for a specific system.
     *
     * @param systemId the system identifier
     * @return list of records for that system
     */
    public List<CalculationRecord> getBySystem(String systemId) {
        return results.values().stream()
            .filter(r -> r.systemId().equals(systemId))
            .collect(Collectors.toList());
    }

    /**
     * Gets all results matching a temperature range.
     *
     * @param minTemp minimum temperature (inclusive)
     * @param maxTemp maximum temperature (inclusive)
     * @return list of matching records
     */
    public List<CalculationRecord> getByTemperatureRange(double minTemp, double maxTemp) {
        return results.values().stream()
            .filter(r -> r.temperature() >= minTemp && r.temperature() <= maxTemp)
            .collect(Collectors.toList());
    }

    /**
     * Gets all results matching a composition range (for binary, uses x_B).
     *
     * @param minComp minimum composition (inclusive)
     * @param maxComp maximum composition (inclusive)
     * @return list of matching records
     */
    public List<CalculationRecord> getByCompositionRange(double minComp, double maxComp) {
        return results.values().stream()
            .filter(r -> {
                double x = r.compositionB();
                return x >= minComp && x <= maxComp;
            })
            .collect(Collectors.toList());
    }

    /**
     * Gets all results completed after the specified date.
     *
     * @param after the cutoff date
     * @return list of records completed after that date
     */
    public List<CalculationRecord> getByDateAfter(LocalDateTime after) {
        return results.values().stream()
            .filter(r -> r.timestamp().isAfter(after))
            .collect(Collectors.toList());
    }

    /**
     * Gets all results for a specific calculation method.
     *
     * @param method CVM or MCS
     * @return list of records using that method
     */
    public List<CalculationRecord> getByMethod(CalculationMethod method) {
        return results.values().stream()
            .filter(r -> r.method() == method)
            .collect(Collectors.toList());
    }

    /**
     * Gets results matching a custom predicate.
     *
     * @param predicate the filter condition
     * @return list of matching records
     */
    public List<CalculationRecord> query(Predicate<CalculationRecord> predicate) {
        return results.values().stream()
            .filter(predicate)
            .collect(Collectors.toList());
    }

    // =========================================================================
    // Lifecycle Management
    // =========================================================================

    /**
     * Removes all results for a system.
     *
     * @param systemId the system to remove results for
     * @return number of results removed
     */
    public int removeBySystem(String systemId) {
        List<String> toRemove = results.entrySet().stream()
            .filter(e -> e.getValue().systemId().equals(systemId))
            .map(Map.Entry::getKey)
            .collect(Collectors.toList());
        
        toRemove.forEach(this::remove);
        return toRemove.size();
    }

    /**
     * Purges results older than the specified number of days.
     *
     * @param daysOld age threshold in days
     * @return number of results purged
     * @throws IOException if file deletion fails
     */
    public int purgeOlderThan(int daysOld) throws IOException {
        LocalDateTime cutoff = LocalDateTime.now().minusDays(daysOld);
        
        List<String> toRemove = results.entrySet().stream()
            .filter(e -> e.getValue().timestamp().isBefore(cutoff))
            .map(Map.Entry::getKey)
            .collect(Collectors.toList());
        
        toRemove.forEach(this::remove);
        
        // Also clean up orphaned files on disk
        long cutoffMs = System.currentTimeMillis() - (daysOld * 24L * 60 * 60 * 1000);
        cleanOrphanedFiles(cutoffMs);
        
        return toRemove.size();
    }

    /**
     * Clears all results (both in memory and on disk).
     *
     * @throws IOException if file deletion fails
     */
    public void clear() throws IOException {
        results.clear();
        if (Files.exists(resultsRoot)) {
            deleteDirectory(resultsRoot);
            Files.createDirectories(resultsRoot);
        }
    }

    // =========================================================================
    // Persistence (Private)
    // =========================================================================

    private void loadResultsFromDisk() {
        if (!Files.exists(resultsRoot)) {
            return;
        }
        
        try {
            Files.walk(resultsRoot)
                .filter(Files::isRegularFile)
                .filter(p -> p.toString().endsWith(".json"))
                .forEach(this::loadResultFile);
        } catch (IOException e) {
            System.err.println("[ResultRepository] Failed to scan results: " + e.getMessage());
        }
    }

    private void loadResultFile(Path file) {
        try {
            String json = Files.readString(file);
            // TODO: Implement JSON deserialization for CalculationRecord
            // For now, result files are placeholders
        } catch (Exception e) {
            System.err.println("[ResultRepository] Failed to load: " + file + " - " + e.getMessage());
        }
    }

    private void persistResult(CalculationRecord record) {
        try {
            Path systemDir = resultsRoot.resolve(record.systemId());
            Files.createDirectories(systemDir);
            
            Path resultFile = systemDir.resolve(record.id() + ".json");
            String json = serializeRecord(record);
            Files.writeString(resultFile, json);
        } catch (IOException e) {
            System.err.println("[ResultRepository] Failed to persist " + record.id() + ": " + e.getMessage());
        }
    }

    private void deleteResultFile(CalculationRecord record) {
        try {
            Path resultFile = resultsRoot.resolve(record.systemId()).resolve(record.id() + ".json");
            Files.deleteIfExists(resultFile);
        } catch (IOException e) {
            System.err.println("[ResultRepository] Failed to delete " + record.id() + ": " + e.getMessage());
        }
    }

    private String serializeRecord(CalculationRecord record) {
        // TODO: Implement proper JSON serialization for CalculationRecord
        // For now, return a minimal JSON stub
        return String.format(
            "{\"id\":\"%s\",\"systemId\":\"%s\",\"method\":\"%s\",\"T\":%.2f,\"G\":%.6f,\"H\":%.6f,\"S\":%.8f}",
            record.id(),
            record.systemId(),
            record.method(),
            record.temperature(),
            record.gibbsEnergy(),
            record.enthalpy(),
            record.entropy()
        );
    }

    private void cleanOrphanedFiles(long cutoffMs) throws IOException {
        if (!Files.exists(resultsRoot)) {
            return;
        }
        
        Files.walk(resultsRoot)
            .filter(Files::isRegularFile)
            .filter(p -> p.toString().endsWith(".json"))
            .filter(p -> p.toFile().lastModified() < cutoffMs)
            .forEach(p -> {
                try {
                    Files.delete(p);
                } catch (IOException e) {
                    System.err.println("[ResultRepository] Failed to delete: " + p);
                }
            });
    }

    private void deleteDirectory(Path path) throws IOException {
        Files.walk(path)
            .sorted(Comparator.reverseOrder())
            .forEach(p -> {
                try {
                    Files.delete(p);
                } catch (IOException e) {
                    System.err.println("[ResultRepository] Failed to delete: " + p);
                }
            });
    }
}

package org.ce.infrastructure.registry;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * Centralized workspace path resolver for the CE Workbench.
 *
 * <p>Owns the layout of {@code ~/.ce-workbench/} and provides typed accessors
 * for every persistent artifact the application produces or consumes:</p>
 *
 * <pre>
 * ~/.ce-workbench/
 * ├── data/
 * │   └── systems/
 * │       └── {cecKey}/          ← user-assembled/edited CEC files
 * │           └── cec.json
 * ├── results/
 * │   └── {systemId}/
 * │       └── {resultId}.json    ← persisted calculation results
 * └── registry/
 *     └── systems.json           ← persisted system registry
 * </pre>
 *
 * <p>Create one instance at application startup and pass it to components that
 * need path resolution (SystemDataLoader, SystemRegistry, ResultRepository).</p>
 */
public class WorkspaceManager {

    private final Path root;

    /**
     * Initialises the workspace under {@code userHome/.ce-workbench/}.
     * All required subdirectories are created if absent.
     *
     * @param userHome user home directory (e.g. {@code Paths.get(System.getProperty("user.home"))})
     * @throws IOException if directory creation fails
     */
    public WorkspaceManager(Path userHome) throws IOException {
        this.root = userHome.resolve(".ce-workbench");
        Files.createDirectories(root.resolve("data/systems"));
        Files.createDirectories(root.resolve("results"));
        Files.createDirectories(root.resolve("registry"));
    }

    // -------------------------------------------------------------------------
    // CEC data paths
    // -------------------------------------------------------------------------

    /**
     * Full path to the {@code cec.json} file for the given cecKey.
     *
     * @param cecKey full key, e.g. {@code "Nb-Ti_BCC_A2_T"}
     */
    public Path cecDataFile(String cecKey) {
        return root.resolve("data/systems").resolve(cecKey).resolve("cec.json");
    }

    /**
     * Directory that should contain {@code cec.json} for the given cecKey.
     * The directory is NOT created by this call.
     *
     * @param cecKey full key, e.g. {@code "Nb-Ti_BCC_A2_T"}
     */
    public Path cecDataDir(String cecKey) {
        return root.resolve("data/systems").resolve(cecKey);
    }

    // -------------------------------------------------------------------------
    // Result paths
    // -------------------------------------------------------------------------

    /**
     * Directory for calculation results belonging to a specific system.
     * The directory is NOT created by this call.
     *
     * @param systemId system identifier (e.g. {@code "Nb-Ti_BCC_A2_T"})
     */
    public Path resultsDir(String systemId) {
        return root.resolve("results").resolve(systemId);
    }

    // -------------------------------------------------------------------------
    // Registry path
    // -------------------------------------------------------------------------

    /**
     * Path to the persisted system registry JSON file.
     */
    public Path registryFile() {
        return root.resolve("registry/systems.json");
    }

    // -------------------------------------------------------------------------
    // Root accessor
    // -------------------------------------------------------------------------

    /**
     * Returns the workspace root directory ({@code ~/.ce-workbench/}).
     */
    public Path getRoot() {
        return root;
    }
}

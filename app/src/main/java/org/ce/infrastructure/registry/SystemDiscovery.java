package org.ce.infrastructure.registry;

import org.json.JSONObject;
import org.ce.domain.system.SystemIdentity;
import java.nio.file.*;
import java.util.*;
import java.util.logging.Logger;
import org.ce.infrastructure.logging.LoggingConfig;

/**
 * Discovers alloy systems automatically from the filesystem.
 *
 * <p>Scans {@code app/src/main/resources/data/systems/} for CEC files and
 * auto-generates {@link SystemIdentity} objects from their metadata.
 * Eliminates the need for manual system registration.</p>
 *
 * <p><b>File Structure Expectation:</b></p>
 * <pre>
 * data/systems/
 *   ├── A-B_BCC_A2_T/
 *   │   └── cec.json          (contains: elements, structure, phase, model)
 *   ├── Nb-Ti_BCC_A2_T/
 *   │   └── cec.json
 *   └── Nb-Ti-V_BCC_A2_T/
 *       └── cec.json
 * </pre>
 *
 * <p><b>Required CEC JSON Fields:</b></p>
 * <pre>
 * {
 *   "elements": "Nb-Ti",           // Hyphen-separated element symbols
 *   "structure": "BCC",            // Crystal structure
 *   "phase": "A2",                 // Phase designation
 *   "model": "T",                  // Model type
 *   "cecValues": [...],
 *   "cecUnits": "J/mol"
 * }
 * </pre>
 *
 * <p><b>Usage:</b></p>
 * <pre>
 * Path systemsRoot = Paths.get("app/src/main/resources/data/systems");
 * List&lt;SystemIdentity&gt; systems = SystemDiscovery.discoverSystems(systemsRoot);
 * for (SystemIdentity system : systems) {
 *     registry.registerSystem(system);
 * }
 * </pre>
 */
public class SystemDiscovery {

    private static final Logger LOG = LoggingConfig.getLogger(SystemDiscovery.class);

    private SystemDiscovery() {
        // Static utility class
    }

    /**
     * Discover all alloy systems from the filesystem.
     *
     * <p>Scans the systems directory for subdirectories containing cec.json files.
     * Each system is parsed and returned as a SystemIdentity.</p>
     *
     * @param systemsRoot Root directory containing system subdirectories
     *                    (e.g., {@code app/src/main/resources/data/systems/})
     * @return List of discovered SystemIdentity objects, empty if none found
     */
    public static List<SystemIdentity> discoverSystems(Path systemsRoot) {
        List<SystemIdentity> systems = new ArrayList<>();

        if (!Files.exists(systemsRoot) || !Files.isDirectory(systemsRoot)) {
            LOG.warning("Systems root not found or not a directory: " + systemsRoot);
            return systems;
        }

        try (DirectoryStream<Path> dirStream = Files.newDirectoryStream(systemsRoot)) {
            for (Path systemDir : dirStream) {
                if (Files.isDirectory(systemDir)) {
                    Path cecFile = systemDir.resolve("cec.json");
                    if (Files.exists(cecFile)) {
                        Optional<SystemIdentity> system = parseSystemIdentity(cecFile, systemDir.getFileName().toString());
                        if (system.isPresent()) {
                            systems.add(system.get());
                            LOG.fine("Discovered system: " + system.get().getId());
                        }
                    }
                }
            }
        } catch (Exception e) {
            LOG.warning("Error scanning systems directory: " + e.getMessage());
        }

        LOG.info("System discovery complete: found " + systems.size() + " systems");
        return systems;
    }

    /**
     * Parse a single system identity from a CEC JSON file.
     *
     * <p>Extracts metadata from the CEC file and creates a SystemIdentity object.
     * The systemId parameter is used as-is (e.g., "A-B_BCC_A2_T").</p>
     *
     * @param cecJsonPath Path to the cec.json file
     * @param systemId    The system identifier (directory name containing cec.json)
     * @return Optional containing the parsed SystemIdentity, or empty if parsing fails
     */
    public static Optional<SystemIdentity> parseSystemIdentity(Path cecJsonPath, String systemId) {
        try {
            String json = Files.readString(cecJsonPath);
            JSONObject cecData = new JSONObject(json);

            // Extract required fields (use optString for safe defaults)
            String elements = cecData.optString("elements", "");
            String structure = cecData.optString("structure", "");
            String phase = cecData.optString("phase", "");
            String model = cecData.optString("model", "");

            // Validate required fields
            if (elements.isEmpty() || structure.isEmpty() || phase.isEmpty() || model.isEmpty()) {
                LOG.warning("CEC file missing required fields: " + cecJsonPath);
                return Optional.empty();
            }

            // Parse components from elements string (e.g., "Nb-Ti" → ["Nb", "Ti"])
            String[] components = parseComponentsFromElements(elements);

            if (components.length == 0) {
                LOG.warning("Could not parse components from elements: " + elements);
                return Optional.empty();
            }

            // Build system name
            String name = buildSystemName(elements, structure, phase, model);

            // Create and return SystemIdentity
            SystemIdentity system = SystemIdentity.builder()
                .id(systemId)
                .name(name)
                .structure(structure)
                .phase(phase)
                .model(model)
                .components(components)
                .clusterFilePath("")  // Not used in current design
                .symmetryGroupName("") // Not used in current design
                .build();

            return Optional.of(system);

        } catch (Exception e) {
            LOG.warning("Failed to parse system from " + cecJsonPath + ": " + e.getMessage());
            return Optional.empty();
        }
    }

    /**
     * Parse component symbols from an elements string.
     *
     * <p>Handles hyphen-separated format (e.g., "Nb-Ti" → ["Nb", "Ti"]).
     * Trims whitespace and filters empty values.</p>
     *
     * @param elementsString Hyphen-separated element symbols (e.g., "Nb-Ti-V")
     * @return Array of element symbols, empty if parsing fails
     */
    private static String[] parseComponentsFromElements(String elementsString) {
        if (elementsString == null || elementsString.isEmpty()) {
            return new String[0];
        }

        String[] parts = elementsString.split("-");
        List<String> components = new ArrayList<>();

        for (String part : parts) {
            String trimmed = part.trim();
            if (!trimmed.isEmpty()) {
                components.add(trimmed);
            }
        }

        return components.toArray(new String[0]);
    }

    /**
     * Build a human-readable system name from metadata.
     *
     * <p>Example: "Nb-Ti BCC A2 (T)"</p>
     *
     * @param elements Elements string (e.g., "Nb-Ti")
     * @param structure Structure type (e.g., "BCC")
     * @param phase Phase designation (e.g., "A2")
     * @param model Model type (e.g., "T")
     * @return Human-readable system name
     */
    private static String buildSystemName(String elements, String structure, String phase, String model) {
        return String.format("%s %s %s (%s)", elements, structure, phase, model);
    }
}

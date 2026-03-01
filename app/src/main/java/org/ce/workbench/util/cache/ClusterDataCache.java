package org.ce.workbench.util.cache;

import org.ce.identification.result.ClusCoordListResult;
import org.json.JSONObject;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Optional;

/**
 * Persists and loads the full {@link ClusCoordListResult} (including orbitList)
 * keyed by <em>component-count + structure + model</em>
 * (e.g., {@code "BCC_A2_T_bin"} for binary, {@code "BCC_A2_T_tern"} for ternary).
 *
 * <p>The key is element-independent: cluster topology for {@code BCC_A2_T_bin} is
 * shared by all binary BCC alloys (Ti-Nb, Nb-V, Fe-Cr, etc.).</p>
 *
 * <p>Load order for {@link #loadClusterData}:</p>
 * <ol>
 *   <li>Classpath / bundled resources (read-only, packaged at build time)</li>
 *   <li>Runtime-written project directory
 *       {@code data/cluster_cache/{clusterKey}/cluster_result.json}</li>
 * </ol>
 *
 * <p>A file is only considered valid if it contains an {@code "orbitList"} key.
 * Old-format files (metadata-only, no orbitList) are ignored.</p>
 */
public class ClusterDataCache {

    private static final String SCHEMA_KEY = "orbitList";

    private ClusterDataCache() {}

    // =========================================================================
    // Public API
    // =========================================================================

    /**
     * Saves the full {@link ClusCoordListResult} under the given model key.
     *
     * @param clusterData result to persist (must not be null)
     * @param modelId     structure/model key, e.g. {@code "BCC_A2_T"}
     * @return {@code true} on success
     */
    public static boolean saveClusterData(ClusCoordListResult clusterData, String modelId)
            throws Exception {

        System.out.println("[ClusterDataCache.save] modelId=" + modelId);

        if (clusterData == null) {
            System.out.println("[ClusterDataCache.save] ERROR: clusterData is null — nothing saved");
            return false;
        }

        System.out.println("[ClusterDataCache.save]   tc=" + clusterData.getTc()
                + "  orbitList size=" + clusterData.getOrbitList().size()
                + "  clusCoordList size=" + clusterData.getClusCoordList().size());

        Path dir  = resolveClusterDir(modelId);
        Path file = dir.resolve("cluster_result.json");
        System.out.println("[ClusterDataCache.save]   target file=" + file.toAbsolutePath());

        Files.createDirectories(dir);
        System.out.println("[ClusterDataCache.save]   directory ensured");

        JSONObject json = serialize(clusterData);
        Files.writeString(file, json.toString(2));

        System.out.println("[ClusterDataCache.save]   written " + Files.size(file)
                + " bytes  keys=" + json.keySet());
        return true;
    }

    /**
     * Loads a full {@link ClusCoordListResult} for the given model key.
     * Returns empty if no valid (full-schema) file is found.
     */
    public static Optional<ClusCoordListResult> loadClusterData(String modelId)
            throws Exception {

        System.out.println("[ClusterDataCache.load] modelId=" + modelId);

        // 1. Bundled classpath resources
        Optional<ClusCoordListResult> bundled = loadFromResources(modelId);
        if (bundled.isPresent()) {
            System.out.println("[ClusterDataCache.load]   source=classpath  tc=" + bundled.get().getTc());
            return bundled;
        }

        // 2. Runtime-written file
        Path file = resolveClusterDir(modelId).resolve("cluster_result.json");
        System.out.println("[ClusterDataCache.load]   checking runtime path: " + file.toAbsolutePath());

        if (!Files.exists(file)) {
            System.out.println("[ClusterDataCache.load]   NOT FOUND at runtime path");
            return Optional.empty();
        }

        String raw = Files.readString(file);
        JSONObject json = new JSONObject(raw);
        System.out.println("[ClusterDataCache.load]   file exists, keys=" + json.keySet());

        if (!json.has(SCHEMA_KEY)) {
            System.out.println("[ClusterDataCache.load]   INVALID: missing '" + SCHEMA_KEY
                    + "' — old-format file, ignoring");
            return Optional.empty();
        }

        ClusCoordListResult result = deserialize(json);
        System.out.println("[ClusterDataCache.load]   deserialized: tc=" + result.getTc()
                + "  orbitList=" + result.getOrbitList().size()
                + "  clusCoordList=" + result.getClusCoordList().size());
        return Optional.of(result);
    }

    /**
     * Returns {@code true} if a valid (full-schema) cluster file exists for
     * the given model key.  Old-format files without {@code "orbitList"} are
     * treated as absent.
     */
    public static boolean clusterDataExists(String modelId) {
        System.out.println("[ClusterDataCache.exists] checking modelId=" + modelId);

        // 1. Classpath
        String resourcePath = "/cluster_cache/" + modelId + "/cluster_result.json";
        try (var is = ClusterDataCache.class.getResourceAsStream(resourcePath)) {
            if (is != null) {
                String raw = new String(is.readAllBytes(), StandardCharsets.UTF_8);
                boolean valid = new JSONObject(raw).has(SCHEMA_KEY);
                System.out.println("[ClusterDataCache.exists]   classpath " + resourcePath
                        + " → " + (valid ? "VALID" : "OLD FORMAT (ignored)"));
                if (valid) return true;
            } else {
                System.out.println("[ClusterDataCache.exists]   classpath " + resourcePath + " → not found");
            }
        } catch (Exception e) {
            System.out.println("[ClusterDataCache.exists]   classpath read error: " + e.getMessage());
        }

        // 2. Runtime file
        try {
            Path file = resolveClusterDir(modelId).resolve("cluster_result.json");
            System.out.println("[ClusterDataCache.exists]   runtime path: " + file.toAbsolutePath());
            if (!Files.exists(file)) {
                System.out.println("[ClusterDataCache.exists]   runtime file NOT FOUND → false");
                return false;
            }
            String raw = Files.readString(file);
            boolean valid = new JSONObject(raw).has(SCHEMA_KEY);
            System.out.println("[ClusterDataCache.exists]   runtime file exists → "
                    + (valid ? "VALID" : "OLD FORMAT (ignored)") + " → " + valid);
            return valid;
        } catch (Exception e) {
            System.out.println("[ClusterDataCache.exists]   runtime check error: " + e.getMessage());
            return false;
        }
    }

    // =========================================================================
    // Serialization — delegates to ClusterDataSerializer
    // =========================================================================

    static JSONObject serialize(ClusCoordListResult data) {
        JSONObject root = ClusterDataSerializer.serializeClusCoordListResult(data);
        root.put("timestamp", System.currentTimeMillis());
        return root;
    }

    static ClusCoordListResult deserialize(JSONObject root) {
        return ClusterDataSerializer.deserializeClusCoordListResult(root);
    }

    // =========================================================================
    // Path resolution
    // =========================================================================

    private static Path resolveClusterDir(String modelId) {
        Path root = findProjectRoot();
        Path dir  = root.resolve("data/cluster_cache").resolve(modelId);
        System.out.println("[ClusterDataCache.path] projectRoot=" + root.toAbsolutePath()
                + "  clusterDir=" + dir.toAbsolutePath());
        return dir;
    }

    private static Optional<ClusCoordListResult> loadFromResources(String modelId) {
        String path = "/cluster_cache/" + modelId + "/cluster_result.json";
        try (var is = ClusterDataCache.class.getResourceAsStream(path)) {
            if (is == null) return Optional.empty();
            String raw  = new String(is.readAllBytes(), StandardCharsets.UTF_8);
            JSONObject j = new JSONObject(raw);
            if (!j.has(SCHEMA_KEY)) {
                System.out.println("[ClusterDataCache.loadFromResources] classpath file "
                        + path + " is old-format (no orbitList) — skipping");
                return Optional.empty();
            }
            return Optional.of(deserialize(j));
        } catch (Exception e) {
            System.err.println("[ClusterDataCache.loadFromResources] error reading "
                    + path + ": " + e.getMessage());
            return Optional.empty();
        }
    }

    /** Walks up from user.dir to find the Gradle project root. */
    private static Path findProjectRoot() {
        Path current = Path.of(System.getProperty("user.dir"));
        System.out.println("[ClusterDataCache.findProjectRoot] user.dir=" + current.toAbsolutePath());
        while (current != null) {
            if (Files.exists(current.resolve("app/build.gradle"))) {
                System.out.println("[ClusterDataCache.findProjectRoot] found (app/build.gradle): " + current);
                return current;
            }
            if (Files.exists(current.resolve("build.gradle"))) {
                Path parent = current.getParent();
                System.out.println("[ClusterDataCache.findProjectRoot] found (build.gradle): " + parent);
                return parent;
            }
            current = current.getParent();
        }
        throw new IllegalStateException(
                "[ClusterDataCache] Cannot locate project root — no build.gradle found "
                + "walking up from " + System.getProperty("user.dir"));
    }
}
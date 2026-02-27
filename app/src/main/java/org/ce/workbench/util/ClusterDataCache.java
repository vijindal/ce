package org.ce.workbench.util;

import org.ce.identification.engine.Cluster;
import org.ce.identification.engine.ClusCoordListResult;
import org.ce.identification.engine.Site;
import org.ce.identification.engine.Sublattice;
import org.ce.identification.engine.Vector3D;
import org.json.JSONArray;
import org.json.JSONObject;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;

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
 *   <li>Runtime-written project source tree
 *       {@code src/main/resources/cluster_data/{clusterKey}/cluster_result.json}</li>
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
        String resourcePath = "/cluster_data/" + modelId + "/cluster_result.json";
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
    // Serialization
    // =========================================================================

    static JSONObject serialize(ClusCoordListResult data) {
        JSONObject root = new JSONObject();
        root.put("tc",                   data.getTc());
        root.put("numPointSubClusFound", data.getNumPointSubClusFound());
        root.put("multiplicities",       doubleListToJson(data.getMultiplicities()));
        root.put("rcList",               rcListToJson(data.getRcList()));
        root.put("clusCoordList",        clusterListToJson(data.getClusCoordList()));
        root.put("orbitList",            orbitListToJson(data.getOrbitList()));
        root.put("timestamp",            System.currentTimeMillis());
        return root;
    }

    static ClusCoordListResult deserialize(JSONObject root) {
        int tc              = root.getInt("tc");
        int numPointSubClus = root.getInt("numPointSubClusFound");
        List<Double> mults  = jsonToDoubleList(root.getJSONArray("multiplicities"));
        List<List<Integer>> rc = jsonToRcList(root.getJSONArray("rcList"));
        List<Cluster> coord    = jsonToClusterList(root.getJSONArray("clusCoordList"));
        List<List<Cluster>> orb = jsonToOrbitList(root.getJSONArray("orbitList"));
        return new ClusCoordListResult(coord, mults, orb, rc, tc, numPointSubClus);
    }

    // =========================================================================
    // Cluster / Sublattice / Site — serialization
    // =========================================================================

    private static JSONArray clusterListToJson(List<Cluster> clusters) {
        JSONArray arr = new JSONArray();
        for (Cluster c : clusters) arr.put(clusterToJson(c));
        return arr;
    }

    private static JSONArray orbitListToJson(List<List<Cluster>> orbits) {
        JSONArray outer = new JSONArray();
        for (List<Cluster> orbit : orbits) outer.put(clusterListToJson(orbit));
        return outer;
    }

    private static JSONObject clusterToJson(Cluster c) {
        JSONArray sublats = new JSONArray();
        for (Sublattice sub : c.getSublattices()) sublats.put(sublatticeToJson(sub));
        JSONObject obj = new JSONObject();
        obj.put("sublattices", sublats);
        return obj;
    }

    private static JSONObject sublatticeToJson(Sublattice sub) {
        JSONArray sitesArr = new JSONArray();
        for (Site s : sub.getSites()) sitesArr.put(siteToJson(s));
        JSONObject obj = new JSONObject();
        obj.put("sites", sitesArr);
        return obj;
    }

    private static JSONObject siteToJson(Site s) {
        JSONObject obj = new JSONObject();
        obj.put("x", s.getPosition().getX());
        obj.put("y", s.getPosition().getY());
        obj.put("z", s.getPosition().getZ());
        if (s.getSymbol() != null) obj.put("symbol", s.getSymbol());
        return obj;
    }

    // =========================================================================
    // Cluster / Sublattice / Site — deserialization
    // =========================================================================

    private static List<Cluster> jsonToClusterList(JSONArray arr) {
        List<Cluster> list = new ArrayList<>();
        for (int i = 0; i < arr.length(); i++) list.add(jsonToCluster(arr.getJSONObject(i)));
        return list;
    }

    private static List<List<Cluster>> jsonToOrbitList(JSONArray arr) {
        List<List<Cluster>> orbits = new ArrayList<>();
        for (int i = 0; i < arr.length(); i++) orbits.add(jsonToClusterList(arr.getJSONArray(i)));
        return orbits;
    }

    private static Cluster jsonToCluster(JSONObject obj) {
        JSONArray sublatsArr = obj.getJSONArray("sublattices");
        List<Sublattice> subs = new ArrayList<>();
        for (int i = 0; i < sublatsArr.length(); i++)
            subs.add(jsonToSublattice(sublatsArr.getJSONObject(i)));
        return new Cluster(subs);
    }

    private static Sublattice jsonToSublattice(JSONObject obj) {
        JSONArray sitesArr = obj.getJSONArray("sites");
        List<Site> sites = new ArrayList<>();
        for (int i = 0; i < sitesArr.length(); i++) sites.add(jsonToSite(sitesArr.getJSONObject(i)));
        return new Sublattice(sites);
    }

    private static Site jsonToSite(JSONObject obj) {
        double x   = obj.getDouble("x");
        double y   = obj.getDouble("y");
        double z   = obj.getDouble("z");
        String sym = obj.has("symbol") ? obj.getString("symbol") : null;
        return new Site(new Vector3D(x, y, z), sym);
    }

    // =========================================================================
    // Primitive helpers
    // =========================================================================

    private static JSONArray doubleListToJson(List<Double> list) {
        JSONArray arr = new JSONArray();
        for (double v : list) arr.put(v);
        return arr;
    }

    private static JSONArray rcListToJson(List<List<Integer>> list) {
        JSONArray outer = new JSONArray();
        for (List<Integer> inner : list) {
            JSONArray ia = new JSONArray();
            for (int v : inner) ia.put(v);
            outer.put(ia);
        }
        return outer;
    }

    private static List<Double> jsonToDoubleList(JSONArray arr) {
        List<Double> list = new ArrayList<>();
        for (int i = 0; i < arr.length(); i++) list.add(arr.getDouble(i));
        return list;
    }

    private static List<List<Integer>> jsonToRcList(JSONArray arr) {
        List<List<Integer>> list = new ArrayList<>();
        for (int i = 0; i < arr.length(); i++) {
            JSONArray inner = arr.getJSONArray(i);
            List<Integer> row = new ArrayList<>();
            for (int j = 0; j < inner.length(); j++) row.add(inner.getInt(j));
            list.add(row);
        }
        return list;
    }

    // =========================================================================
    // Path resolution
    // =========================================================================

    private static Path resolveClusterDir(String modelId) {
        Path root = findProjectRoot();
        Path dir  = root.resolve("src/main/resources/cluster_data").resolve(modelId);
        System.out.println("[ClusterDataCache.path] projectRoot=" + root.toAbsolutePath()
                + "  clusterDir=" + dir.toAbsolutePath());
        return dir;
    }

    private static Optional<ClusCoordListResult> loadFromResources(String modelId) {
        String path = "/cluster_data/" + modelId + "/cluster_result.json";
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
package org.ce.workbench.util;

import org.ce.identification.engine.ClusCoordListResult;
import org.json.JSONArray;
import org.json.JSONObject;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;

/**
 * Utility for persisting and loading ClusCoordListResult cluster data to/from project folder.
 * 
 * <p>Stores cluster data in JSON format at: {@code {project}/src/main/resources/cluster_data/{systemId}/cluster_result.json}</p>
 * <p>Enables distribution of example cluster data with the application for users to work with.</p>
 *
 * @author  CVM Project
 * @version 1.0
 */
public class ClusterDataCache {

    private ClusterDataCache() {}

    // ========================================================================
    // Save cluster data to project resources
    // ========================================================================

    /**
     * Saves cluster data to the project resources folder for distribution.
     * 
     * <p>Path: {@code src/main/resources/cluster_data/{systemId}/cluster_result.json}</p>
     * <p>This method serializes the essential cluster type information (tc, multiplicities, rcList)
     * to JSON. The Cluster objects themselves (orbitList, clusCoordList) are reconstructable
     * from the cluster algebra file.</p>
     *
     * @param clusterData the cluster identification result to persist
     * @param systemId    the system identifier (e.g., "BCC_A2_T_Ti-Nb")
     * @return {@code true} if save succeeded, {@code false} if cluster data is null
     * @throws Exception if I/O or JSON serialization fails
     */
    public static boolean saveClusterData(ClusCoordListResult clusterData, String systemId) throws Exception {
        System.out.println("[ClusterDataCache] saveClusterData called for: " + systemId);
        
        if (clusterData == null) {
            System.out.println("[ClusterDataCache] ✗ clusterData is null");
            return false;
        }
        
        System.out.println("[ClusterDataCache] Cluster data: tc=" + clusterData.getTc() + ", multiplicities=" + clusterData.getMultiplicities().size());

        // Get project root by navigating from user.home
        Path projectRoot = findProjectRoot();
        System.out.println("[ClusterDataCache] Project root: " + projectRoot);
        
        Path clusterDir = projectRoot
            .resolve("src/main/resources/cluster_data")
            .resolve(systemId);
        System.out.println("[ClusterDataCache] Target directory: " + clusterDir);

        Files.createDirectories(clusterDir);
        System.out.println("[ClusterDataCache] Directory created/exists");

        Path jsonFile = clusterDir.resolve("cluster_result.json");
        System.out.println("[ClusterDataCache] Target file: " + jsonFile);
        
        JSONObject json = serializeClusterData(clusterData);
        System.out.println("[ClusterDataCache] JSON serialized: " + json.toString().substring(0, Math.min(200, json.toString().length())) + "...");
        
        Files.writeString(jsonFile, json.toString(2)); // Pretty-print with 2-space indent
        System.out.println("[ClusterDataCache] ✓ File written successfully");
        System.out.println("[ClusterDataCache] File size: " + Files.size(jsonFile) + " bytes");
        
        return true;
    }

    // ========================================================================
    // Load cluster data from project resources
    // ========================================================================

    /**
     * Loads cluster metadata from project resources (essential info only).
     * 
     * <p>Returns Optional containing tc and multiplicities. Full Cluster objects
     * would require access to the cluster algebra file and regeneration algorithm.</p>
     *
     * @param systemId the system identifier (e.g., "BCC_A2_T_Ti-Nb")
     * @return Optional containing cluster metadata (tc, multiplicities, rcList) if found
     * @throws Exception if I/O or JSON deserialization fails
     */
    public static Optional<ClusterMetadata> loadClusterMetadata(String systemId) throws Exception {
        Path projectRoot = findProjectRoot();
        Path jsonFile = projectRoot
            .resolve("src/main/resources/cluster_data")
            .resolve(systemId)
            .resolve("cluster_result.json");

        if (!Files.exists(jsonFile)) {
            return Optional.empty();
        }

        String content = Files.readString(jsonFile);
        JSONObject json = new JSONObject(content);
        
        int tc = json.getInt("tc");
        int numPointSubClusFound = json.getInt("numPointSubClusFound");
        List<Double> multiplicities = jsonArrayToDoubleList(json.getJSONArray("multiplicities"));
        List<List<Integer>> rcList = jsonArrayToIntListList(json.getJSONArray("rcList"));

        ClusterMetadata metadata = new ClusterMetadata(tc, numPointSubClusFound, multiplicities, rcList);
        return Optional.of(metadata);
    }

    /**
     * Checks if cluster data exists in project resources for the given system.
     *
     * @param systemId the system identifier (e.g., "BCC_A2_T_Ti-Nb")
     * @return {@code true} if cluster_result.json exists and is readable
     */
    public static boolean clusterDataExists(String systemId) {
        try {
            Path projectRoot = findProjectRoot();
            Path jsonFile = projectRoot
                .resolve("src/main/resources/cluster_data")
                .resolve(systemId)
                .resolve("cluster_result.json");
            return Files.exists(jsonFile) && Files.isReadable(jsonFile);
        } catch (Exception e) {
            return false;
        }
    }

    /**
     * Gets the storage location of cluster data in project resources.
     *
     * @param systemId the system identifier (e.g., "BCC_A2_T_Ti-Nb")
     * @return the absolute path to the cluster data directory
     */
    public static Path getClusterDataPath(String systemId) {
        try {
            Path projectRoot = findProjectRoot();
            return projectRoot
                .resolve("src/main/resources/cluster_data")
                .resolve(systemId);
        } catch (Exception e) {
            return null;
        }
    }

    // ========================================================================
    // Serialization helpers
    // ========================================================================

    /**
     * Serializes ClusCoordListResult to JSON object.
     * 
     * <p>Stores: tc, numPointSubClusFound, multiplicities, rcList</p>
     */
    private static JSONObject serializeClusterData(ClusCoordListResult data) {
        JSONObject json = new JSONObject();
        
        json.put("tc", data.getTc());
        json.put("numPointSubClusFound", data.getNumPointSubClusFound());
        json.put("multiplicities", doubleListToJsonArray(data.getMultiplicities()));
        json.put("rcList", intListListToJsonArray(data.getRcList()));
        json.put("timestamp", System.currentTimeMillis());
        
        return json;
    }

    /**
     * Converts List<Double> to JSONArray.
     */
    private static JSONArray doubleListToJsonArray(List<Double> list) {
        JSONArray arr = new JSONArray();
        for (Double val : list) {
            arr.put(val);
        }
        return arr;
    }

    /**
     * Converts List<List<Integer>> to JSONArray (nested).
     */
    private static JSONArray intListListToJsonArray(List<List<Integer>> list) {
        JSONArray outer = new JSONArray();
        for (List<Integer> inner : list) {
            JSONArray innerArr = new JSONArray();
            for (Integer val : inner) {
                innerArr.put(val);
            }
            outer.put(innerArr);
        }
        return outer;
    }

    /**
     * Converts JSONArray to List<Double>.
     */
    private static List<Double> jsonArrayToDoubleList(JSONArray arr) {
        List<Double> list = new ArrayList<>();
        for (int i = 0; i < arr.length(); i++) {
            list.add(arr.getDouble(i));
        }
        return list;
    }

    /**
     * Converts JSONArray to List<List<Integer>>.
     */
    private static List<List<Integer>> jsonArrayToIntListList(JSONArray arr) {
        List<List<Integer>> list = new ArrayList<>();
        for (int i = 0; i < arr.length(); i++) {
            JSONArray innerArr = arr.getJSONArray(i);
            List<Integer> inner = new ArrayList<>();
            for (int j = 0; j < innerArr.length(); j++) {
                inner.add(innerArr.getInt(j));
            }
            list.add(inner);
        }
        return list;
    }

    // ========================================================================
    // Utility: Find project root
    // ========================================================================

    /**
     * Locates the project root by navigating upward from current directory.
     * Looks for build.gradle file as marker.
     *
     * @return Path to project root (directory containing build.gradle)
     * @throws IllegalStateException if project root cannot be found
     */
    private static Path findProjectRoot() {
        Path current = Path.of(System.getProperty("user.dir"));
        
        // If already at app or project root, navigate appropriately
        while (current != null && !current.toString().isEmpty()) {
            if (Files.exists(current.resolve("build.gradle"))) {
                // Found app directory
                return current.getParent(); // Return parent (project root)
            }
            if (Files.exists(current.resolve("app/build.gradle"))) {
                // Found project root
                return current;
            }
            current = current.getParent();
        }

        throw new IllegalStateException(
            "Cannot locate project root. Expected build.gradle in app/ directory.");
    }

    // ========================================================================
    // Metadata container
    // ========================================================================

    /**
     * Immutable container for essential cluster identification metadata.
     * Stores only the information needed for MCS calculations.
     */
    public static class ClusterMetadata {
        private final int tc;
        private final int numPointSubClusFound;
        private final List<Double> multiplicities;
        private final List<List<Integer>> rcList;

        public ClusterMetadata(int tc, int numPointSubClusFound, 
                             List<Double> multiplicities, List<List<Integer>> rcList) {
            this.tc = tc;
            this.numPointSubClusFound = numPointSubClusFound;
            this.multiplicities = multiplicities;
            this.rcList = rcList;
        }

        public int getTc() { return tc; }
        public int getNumPointSubClusFound() { return numPointSubClusFound; }
        public List<Double> getMultiplicities() { return multiplicities; }
        public List<List<Integer>> getRcList() { return rcList; }
    }
}

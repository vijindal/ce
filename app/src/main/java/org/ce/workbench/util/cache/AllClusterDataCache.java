package org.ce.workbench.util.cache;

import org.ce.cvm.CMatrixResult;
import org.ce.identification.cf.CFIdentificationResult;
import org.ce.identification.cluster.ClusterIdentificationResult;
import org.ce.identification.result.ClassifiedClusterResult;
import org.ce.identification.result.ClusCoordListResult;
import org.ce.identification.result.GroupedCFResult;
import org.ce.workbench.backend.data.AllClusterData;
import org.json.JSONArray;
import org.json.JSONObject;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * Persists and loads the full {@link AllClusterData} (Stages 1–3) as a single
 * JSON file, keyed by the element-independent cluster key
 * (e.g. {@code "BCC_A2_T_bin"}).
 *
 * <p>File layout:
 * <pre>
 *   data/cluster_cache/{clusterKey}/all_cluster_data.json
 * </pre>
 *
 * <p>This replaces nothing — it supplements {@link ClusterDataCache} which
 * persists only the MCS-relevant {@link ClusCoordListResult}.</p>
 */
public final class AllClusterDataCache {

    private static final String FILE_NAME = "all_cluster_data.json";

    private AllClusterDataCache() {}

    // =========================================================================
    // Public API
    // =========================================================================

    /**
     * Saves all three stage results under the given cluster key.
     *
     * @param data       complete cluster data (must be non-null; stages may be null)
     * @param clusterKey element-independent key, e.g. {@code "BCC_A2_T_bin"}
     * @return true on success
     */
    public static boolean save(AllClusterData data, String clusterKey) throws Exception {
        System.out.println("[AllClusterDataCache.save] clusterKey=" + clusterKey);
        if (data == null) {
            System.out.println("[AllClusterDataCache.save] ERROR: data is null");
            return false;
        }

        Path dir  = resolveDir(clusterKey);
        Path file = dir.resolve(FILE_NAME);
        Files.createDirectories(dir);

        JSONObject json = serialize(data);
        Files.writeString(file, json.toString(2));

        System.out.println("[AllClusterDataCache.save] written " + Files.size(file)
                + " bytes to " + file.toAbsolutePath());
        return true;
    }

    /**
     * Loads all cluster data for the given key.  Returns empty if no file
     * exists or the file is invalid.
     */
    public static Optional<AllClusterData> load(String clusterKey) throws Exception {
        System.out.println("[AllClusterDataCache.load] clusterKey=" + clusterKey);

        // 1. Classpath
        Optional<AllClusterData> bundled = loadFromResources(clusterKey);
        if (bundled.isPresent()) return bundled;

        // 2. Runtime file
        Path file = resolveDir(clusterKey).resolve(FILE_NAME);
        if (!Files.exists(file)) {
            System.out.println("[AllClusterDataCache.load] NOT FOUND");
            return Optional.empty();
        }

        String raw = Files.readString(file);
        AllClusterData result = deserialize(new JSONObject(raw));
        System.out.println("[AllClusterDataCache.load] loaded: " + result);
        return Optional.of(result);
    }

    /**
     * Returns true if a valid file exists (classpath or runtime).
     */
    public static boolean exists(String clusterKey) {
        // classpath
        String resPath = "/cluster_cache/" + clusterKey + "/" + FILE_NAME;
        try (var is = AllClusterDataCache.class.getResourceAsStream(resPath)) {
            if (is != null) return true;
        } catch (Exception ignored) {}

        // runtime
        try {
            return Files.exists(resolveDir(clusterKey).resolve(FILE_NAME));
        } catch (Exception e) {
            return false;
        }
    }

    // =========================================================================
    // Top-level serialization
    // =========================================================================

    static JSONObject serialize(AllClusterData data) {
        JSONObject root = new JSONObject();
        root.put("systemId",          data.getSystemId());
        root.put("numComponents",     data.getNumComponents());
        root.put("computationTimeMs", data.getComputationTimeMs());
        root.put("timestamp",         System.currentTimeMillis());

        if (data.getStage1() != null) root.put("stage1", serializeStage1(data.getStage1()));
        if (data.getStage2() != null) root.put("stage2", serializeStage2(data.getStage2()));
        if (data.getStage3() != null) root.put("stage3", serializeStage3(data.getStage3()));

        return root;
    }

    static AllClusterData deserialize(JSONObject root) {
        String systemId     = root.getString("systemId");
        int    numComp      = root.getInt("numComponents");
        long   compTime     = root.optLong("computationTimeMs", 0L);

        ClusterIdentificationResult s1 = root.has("stage1")
                ? deserializeStage1(root.getJSONObject("stage1")) : null;
        CFIdentificationResult s2 = root.has("stage2")
                ? deserializeStage2(root.getJSONObject("stage2")) : null;
        CMatrixResult s3 = root.has("stage3")
                ? deserializeStage3(root.getJSONObject("stage3")) : null;

        return new AllClusterData(systemId, numComp, s1, s2, s3, compTime);
    }

    // =========================================================================
    // Stage 1: ClusterIdentificationResult
    // =========================================================================

    private static JSONObject serializeStage1(ClusterIdentificationResult s1) {
        JSONObject obj = new JSONObject();
        obj.put("disClusterData",   ClusterDataSerializer.serializeClusCoordListResult(s1.getDisClusterData()));
        obj.put("nijTable",         ClusterDataSerializer.int2DToJson(s1.getNijTable()));
        obj.put("kbCoefficients",   ClusterDataSerializer.doubleArrayToJson(s1.getKbCoefficients()));
        obj.put("phaseClusterData", ClusterDataSerializer.serializeClusCoordListResult(s1.getPhaseClusterData()));
        obj.put("ordClusterData",   ClusterDataSerializer.serializeClassifiedClusterResult(s1.getOrdClusterData()));
        obj.put("lc",               ClusterDataSerializer.intArrayToJson(s1.getLc()));
        obj.put("mh",               ClusterDataSerializer.double2DToJson(s1.getMh()));
        obj.put("tcdis",            s1.getTcdis());
        obj.put("nxcdis",           s1.getNxcdis());
        obj.put("tc",               s1.getTc());
        obj.put("nxc",              s1.getNxc());
        return obj;
    }

    private static ClusterIdentificationResult deserializeStage1(JSONObject obj) {
        ClusCoordListResult    disCluster   = ClusterDataSerializer.deserializeClusCoordListResult(obj.getJSONObject("disClusterData"));
        int[][]                nijTable     = ClusterDataSerializer.jsonToInt2D(obj.getJSONArray("nijTable"));
        double[]               kb           = ClusterDataSerializer.jsonToDoubleArray(obj.getJSONArray("kbCoefficients"));
        ClusCoordListResult    phaseCluster = ClusterDataSerializer.deserializeClusCoordListResult(obj.getJSONObject("phaseClusterData"));
        ClassifiedClusterResult ordCluster  = ClusterDataSerializer.deserializeClassifiedClusterResult(obj.getJSONObject("ordClusterData"));
        int[]                  lc           = ClusterDataSerializer.jsonToIntArray(obj.getJSONArray("lc"));
        double[][]             mh           = ClusterDataSerializer.jsonToDouble2D(obj.getJSONArray("mh"));
        int tcdis  = obj.getInt("tcdis");
        int nxcdis = obj.getInt("nxcdis");
        int tc     = obj.getInt("tc");
        int nxc    = obj.getInt("nxc");

        return new ClusterIdentificationResult(
                disCluster, nijTable, kb, phaseCluster, ordCluster,
                lc, mh, tcdis, nxcdis, tc, nxc);
    }

    // =========================================================================
    // Stage 2: CFIdentificationResult
    // =========================================================================

    private static JSONObject serializeStage2(CFIdentificationResult s2) {
        JSONObject obj = new JSONObject();
        obj.put("disCFData",      ClusterDataSerializer.serializeClusCoordListResult(s2.getDisCFData()));
        obj.put("tcfdis",         s2.getTcfdis());
        obj.put("phaseCFData",    ClusterDataSerializer.serializeClusCoordListResult(s2.getPhaseCFData()));
        obj.put("ordCFData",      ClusterDataSerializer.serializeClassifiedClusterResult(s2.getOrdCFData()));
        obj.put("groupedCFData",  ClusterDataSerializer.serializeGroupedCFResult(s2.getGroupedCFData()));
        obj.put("lcf",            ClusterDataSerializer.int2DToJson(s2.getLcf()));
        obj.put("tcf",            s2.getTcf());
        obj.put("nxcf",           s2.getNxcf());
        obj.put("ncf",            s2.getNcf());
        return obj;
    }

    private static CFIdentificationResult deserializeStage2(JSONObject obj) {
        ClusCoordListResult     disCF      = ClusterDataSerializer.deserializeClusCoordListResult(obj.getJSONObject("disCFData"));
        int                     tcfdis     = obj.getInt("tcfdis");
        ClusCoordListResult     phaseCF    = ClusterDataSerializer.deserializeClusCoordListResult(obj.getJSONObject("phaseCFData"));
        ClassifiedClusterResult ordCF      = ClusterDataSerializer.deserializeClassifiedClusterResult(obj.getJSONObject("ordCFData"));
        GroupedCFResult         groupedCF  = ClusterDataSerializer.deserializeGroupedCFResult(obj.getJSONObject("groupedCFData"));
        int[][]                 lcf        = ClusterDataSerializer.jsonToInt2D(obj.getJSONArray("lcf"));
        int tcf  = obj.getInt("tcf");
        int nxcf = obj.getInt("nxcf");
        int ncf  = obj.getInt("ncf");

        return new CFIdentificationResult(
                disCF, tcfdis, phaseCF, ordCF, groupedCF,
                lcf, tcf, nxcf, ncf);
    }

    // =========================================================================
    // Stage 3: CMatrixResult
    // =========================================================================

    private static JSONObject serializeStage3(CMatrixResult s3) {
        JSONObject obj = new JSONObject();
        // cmat: List<List<double[][]>>
        JSONArray cmatOuter = new JSONArray();
        for (List<double[][]> innerList : s3.getCmat()) {
            JSONArray row = new JSONArray();
            for (double[][] mat : innerList) row.put(ClusterDataSerializer.double2DToJson(mat));
            cmatOuter.put(row);
        }
        obj.put("cmat", cmatOuter);

        // lcv: int[][]
        obj.put("lcv", ClusterDataSerializer.int2DToJson(s3.getLcv()));

        // wcv: List<List<int[]>>
        JSONArray wcvOuter = new JSONArray();
        for (List<int[]> innerList : s3.getWcv()) {
            JSONArray row = new JSONArray();
            for (int[] arr : innerList) row.put(ClusterDataSerializer.intArrayToJson(arr));
            wcvOuter.put(row);
        }
        obj.put("wcv", wcvOuter);

        return obj;
    }

    private static CMatrixResult deserializeStage3(JSONObject obj) {
        // cmat
        JSONArray cmatArr = obj.getJSONArray("cmat");
        List<List<double[][]>> cmat = new ArrayList<>();
        for (int i = 0; i < cmatArr.length(); i++) {
            JSONArray rowArr = cmatArr.getJSONArray(i);
            List<double[][]> row = new ArrayList<>();
            for (int j = 0; j < rowArr.length(); j++)
                row.add(ClusterDataSerializer.jsonToDouble2D(rowArr.getJSONArray(j)));
            cmat.add(row);
        }

        // lcv
        int[][] lcv = ClusterDataSerializer.jsonToInt2D(obj.getJSONArray("lcv"));

        // wcv
        JSONArray wcvArr = obj.getJSONArray("wcv");
        List<List<int[]>> wcv = new ArrayList<>();
        for (int i = 0; i < wcvArr.length(); i++) {
            JSONArray rowArr = wcvArr.getJSONArray(i);
            List<int[]> row = new ArrayList<>();
            for (int j = 0; j < rowArr.length(); j++)
                row.add(ClusterDataSerializer.jsonToIntArray(rowArr.getJSONArray(j)));
            wcv.add(row);
        }

        return new CMatrixResult(cmat, lcv, wcv);
    }

    // =========================================================================
    // Path resolution  (mirrors ClusterDataCache)
    // =========================================================================

    /**
     * Returns the directory where cluster data for the given key is (or would be) stored.
     * Useful for displaying the storage path in the UI.
     */
    public static Path resolveDir(String clusterKey) {
        Path root = findProjectRoot();
        return root.resolve("data/cluster_cache").resolve(clusterKey);
    }

    private static Optional<AllClusterData> loadFromResources(String clusterKey) {
        String path = "/cluster_cache/" + clusterKey + "/" + FILE_NAME;
        try (var is = AllClusterDataCache.class.getResourceAsStream(path)) {
            if (is == null) return Optional.empty();
            String raw = new String(is.readAllBytes(), StandardCharsets.UTF_8);
            return Optional.of(deserialize(new JSONObject(raw)));
        } catch (Exception e) {
            System.err.println("[AllClusterDataCache.loadFromResources] error: " + e.getMessage());
            return Optional.empty();
        }
    }

    /** Walks up from user.dir to find the Gradle project root. */
    private static Path findProjectRoot() {
        Path current = Path.of(System.getProperty("user.dir"));
        while (current != null) {
            if (Files.exists(current.resolve("app/build.gradle"))) return current;
            if (Files.exists(current.resolve("build.gradle"))) {
                Path parent = current.getParent();
                return parent;
            }
            current = current.getParent();
        }
        throw new IllegalStateException(
                "[AllClusterDataCache] Cannot locate project root from " + System.getProperty("user.dir"));
    }
}

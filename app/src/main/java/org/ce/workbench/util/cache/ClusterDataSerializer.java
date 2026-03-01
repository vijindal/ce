package org.ce.workbench.util.cache;

import org.ce.identification.geometry.Cluster;
import org.ce.identification.result.ClusCoordListResult;
import org.ce.identification.result.ClassifiedClusterResult;
import org.ce.identification.result.GroupedCFResult;
import org.ce.identification.geometry.Site;
import org.ce.identification.geometry.Sublattice;
import org.ce.identification.geometry.Vector3D;
import org.json.JSONArray;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.List;

/**
 * Centralised JSON serialization / deserialization for cluster-related domain
 * objects.  Used by both {@link ClusterDataCache} (MCS-only cache) and
 * {@link AllClusterDataCache} (full CVM cache).
 *
 * <p>All methods are stateless and thread-safe.</p>
 */
public final class ClusterDataSerializer {

    private ClusterDataSerializer() {}

    // =========================================================================
    // ClusCoordListResult
    // =========================================================================

    public static JSONObject serializeClusCoordListResult(ClusCoordListResult data) {
        JSONObject root = new JSONObject();
        root.put("tc",                   data.getTc());
        root.put("numPointSubClusFound", data.getNumPointSubClusFound());
        root.put("multiplicities",       doubleListToJson(data.getMultiplicities()));
        root.put("rcList",               intListListToJson(data.getRcList()));
        root.put("clusCoordList",        clusterListToJson(data.getClusCoordList()));
        root.put("orbitList",            orbitListToJson(data.getOrbitList()));
        return root;
    }

    public static ClusCoordListResult deserializeClusCoordListResult(JSONObject root) {
        int tc              = root.getInt("tc");
        int numPointSubClus = root.getInt("numPointSubClusFound");
        List<Double>           mults = jsonToDoubleList(root.getJSONArray("multiplicities"));
        List<List<Integer>>    rc    = jsonToIntListList(root.getJSONArray("rcList"));
        List<Cluster>          coord = jsonToClusterList(root.getJSONArray("clusCoordList"));
        List<List<Cluster>>    orb   = jsonToOrbitList(root.getJSONArray("orbitList"));
        return new ClusCoordListResult(coord, mults, orb, rc, tc, numPointSubClus);
    }

    // =========================================================================
    // ClassifiedClusterResult
    // =========================================================================

    public static JSONObject serializeClassifiedClusterResult(ClassifiedClusterResult data) {
        JSONObject root = new JSONObject();
        // coordList: List<List<Cluster>>
        root.put("coordList", listOfClusterListToJson(data.getCoordList()));
        // multiplicityList: List<List<Double>>
        root.put("multiplicityList", listOfDoubleListToJson(data.getMultiplicityList()));
        // orbitList: List<List<List<Cluster>>>
        root.put("orbitList", listOfOrbitListToJson(data.getOrbitList()));
        // rcList: List<List<List<Integer>>>
        root.put("rcList", listOfIntListListToJson(data.getRcList()));
        return root;
    }

    public static ClassifiedClusterResult deserializeClassifiedClusterResult(JSONObject root) {
        List<List<Cluster>>          coordList  = jsonToListOfClusterList(root.getJSONArray("coordList"));
        List<List<Double>>           multList   = jsonToListOfDoubleList(root.getJSONArray("multiplicityList"));
        List<List<List<Cluster>>>    orbitList  = jsonToListOfOrbitList(root.getJSONArray("orbitList"));
        List<List<List<Integer>>>    rcList     = jsonToListOfIntListList(root.getJSONArray("rcList"));
        return new ClassifiedClusterResult(coordList, multList, orbitList, rcList);
    }

    // =========================================================================
    // GroupedCFResult
    // =========================================================================

    public static JSONObject serializeGroupedCFResult(GroupedCFResult data) {
        JSONObject root = new JSONObject();
        // coordData: List<List<List<Cluster>>>
        root.put("coordData", listOfOrbitListToJson(data.getCoordData()));
        // multiplicityData: List<List<List<Double>>>
        root.put("multiplicityData", list3DDoubleToJson(data.getMultiplicityData()));
        // orbitData: List<List<List<List<Cluster>>>>
        root.put("orbitData", list4DClusterToJson(data.getOrbitData()));
        // rcData: List<List<List<List<Integer>>>>
        root.put("rcData", list4DIntToJson(data.getRcData()));
        return root;
    }

    public static GroupedCFResult deserializeGroupedCFResult(JSONObject root) {
        List<List<List<Cluster>>>          coordData = jsonToListOfOrbitList(root.getJSONArray("coordData"));
        List<List<List<Double>>>           multData  = jsonToList3DDouble(root.getJSONArray("multiplicityData"));
        List<List<List<List<Cluster>>>>    orbitData = jsonToList4DCluster(root.getJSONArray("orbitData"));
        List<List<List<List<Integer>>>>    rcData    = jsonToList4DInt(root.getJSONArray("rcData"));
        return new GroupedCFResult(coordData, multData, orbitData, rcData);
    }

    // =========================================================================
    // Primitive arrays
    // =========================================================================

    public static JSONArray doubleArrayToJson(double[] arr) {
        JSONArray ja = new JSONArray();
        for (double v : arr) ja.put(v);
        return ja;
    }

    public static double[] jsonToDoubleArray(JSONArray ja) {
        double[] arr = new double[ja.length()];
        for (int i = 0; i < ja.length(); i++) arr[i] = ja.getDouble(i);
        return arr;
    }

    public static JSONArray intArrayToJson(int[] arr) {
        JSONArray ja = new JSONArray();
        for (int v : arr) ja.put(v);
        return ja;
    }

    public static int[] jsonToIntArray(JSONArray ja) {
        int[] arr = new int[ja.length()];
        for (int i = 0; i < ja.length(); i++) arr[i] = ja.getInt(i);
        return arr;
    }

    /** Serialize int[][] as JSON array of arrays. */
    public static JSONArray int2DToJson(int[][] mat) {
        JSONArray outer = new JSONArray();
        for (int[] row : mat) outer.put(intArrayToJson(row));
        return outer;
    }

    /** Deserialize int[][] from JSON array of arrays. */
    public static int[][] jsonToInt2D(JSONArray ja) {
        int[][] mat = new int[ja.length()][];
        for (int i = 0; i < ja.length(); i++) mat[i] = jsonToIntArray(ja.getJSONArray(i));
        return mat;
    }

    /** Serialize double[][] as JSON array of arrays. */
    public static JSONArray double2DToJson(double[][] mat) {
        JSONArray outer = new JSONArray();
        for (double[] row : mat) outer.put(doubleArrayToJson(row));
        return outer;
    }

    /** Deserialize double[][] from JSON array of arrays. */
    public static double[][] jsonToDouble2D(JSONArray ja) {
        double[][] mat = new double[ja.length()][];
        for (int i = 0; i < ja.length(); i++) mat[i] = jsonToDoubleArray(ja.getJSONArray(i));
        return mat;
    }

    // =========================================================================
    // Cluster / Sublattice / Site
    // =========================================================================

    public static JSONObject clusterToJson(Cluster c) {
        JSONArray sublats = new JSONArray();
        for (Sublattice sub : c.getSublattices()) sublats.put(sublatticeToJson(sub));
        JSONObject obj = new JSONObject();
        obj.put("sublattices", sublats);
        return obj;
    }

    public static Cluster jsonToCluster(JSONObject obj) {
        JSONArray sublatsArr = obj.getJSONArray("sublattices");
        List<Sublattice> subs = new ArrayList<>();
        for (int i = 0; i < sublatsArr.length(); i++)
            subs.add(jsonToSublattice(sublatsArr.getJSONObject(i)));
        return new Cluster(subs);
    }

    private static JSONObject sublatticeToJson(Sublattice sub) {
        JSONArray sitesArr = new JSONArray();
        for (Site s : sub.getSites()) sitesArr.put(siteToJson(s));
        JSONObject obj = new JSONObject();
        obj.put("sites", sitesArr);
        return obj;
    }

    private static Sublattice jsonToSublattice(JSONObject obj) {
        JSONArray sitesArr = obj.getJSONArray("sites");
        List<Site> sites = new ArrayList<>();
        for (int i = 0; i < sitesArr.length(); i++) sites.add(jsonToSite(sitesArr.getJSONObject(i)));
        return new Sublattice(sites);
    }

    private static JSONObject siteToJson(Site s) {
        JSONObject obj = new JSONObject();
        obj.put("x", s.getPosition().getX());
        obj.put("y", s.getPosition().getY());
        obj.put("z", s.getPosition().getZ());
        if (s.getSymbol() != null) obj.put("symbol", s.getSymbol());
        return obj;
    }

    private static Site jsonToSite(JSONObject obj) {
        double x   = obj.getDouble("x");
        double y   = obj.getDouble("y");
        double z   = obj.getDouble("z");
        String sym = obj.has("symbol") ? obj.getString("symbol") : null;
        return new Site(new Vector3D(x, y, z), sym);
    }

    // =========================================================================
    // List<Cluster>  &  List<List<Cluster>>  (orbit lists)
    // =========================================================================

    public static JSONArray clusterListToJson(List<Cluster> clusters) {
        JSONArray arr = new JSONArray();
        for (Cluster c : clusters) arr.put(clusterToJson(c));
        return arr;
    }

    public static List<Cluster> jsonToClusterList(JSONArray arr) {
        List<Cluster> list = new ArrayList<>();
        for (int i = 0; i < arr.length(); i++) list.add(jsonToCluster(arr.getJSONObject(i)));
        return list;
    }

    public static JSONArray orbitListToJson(List<List<Cluster>> orbits) {
        JSONArray outer = new JSONArray();
        for (List<Cluster> orbit : orbits) outer.put(clusterListToJson(orbit));
        return outer;
    }

    public static List<List<Cluster>> jsonToOrbitList(JSONArray arr) {
        List<List<Cluster>> orbits = new ArrayList<>();
        for (int i = 0; i < arr.length(); i++) orbits.add(jsonToClusterList(arr.getJSONArray(i)));
        return orbits;
    }

    // =========================================================================
    // List<List<Cluster>>  (classified coord lists — same structure as orbit)
    // =========================================================================

    static JSONArray listOfClusterListToJson(List<List<Cluster>> data) {
        return orbitListToJson(data);  // same shape
    }

    static List<List<Cluster>> jsonToListOfClusterList(JSONArray arr) {
        return jsonToOrbitList(arr);
    }

    // =========================================================================
    // List<List<List<Cluster>>>
    // =========================================================================

    static JSONArray listOfOrbitListToJson(List<List<List<Cluster>>> data) {
        JSONArray outer = new JSONArray();
        for (List<List<Cluster>> orbits : data) outer.put(orbitListToJson(orbits));
        return outer;
    }

    static List<List<List<Cluster>>> jsonToListOfOrbitList(JSONArray arr) {
        List<List<List<Cluster>>> result = new ArrayList<>();
        for (int i = 0; i < arr.length(); i++) result.add(jsonToOrbitList(arr.getJSONArray(i)));
        return result;
    }

    // =========================================================================
    // List<List<List<List<Cluster>>>>
    // =========================================================================

    static JSONArray list4DClusterToJson(List<List<List<List<Cluster>>>> data) {
        JSONArray outer = new JSONArray();
        for (List<List<List<Cluster>>> d : data) outer.put(listOfOrbitListToJson(d));
        return outer;
    }

    static List<List<List<List<Cluster>>>> jsonToList4DCluster(JSONArray arr) {
        List<List<List<List<Cluster>>>> result = new ArrayList<>();
        for (int i = 0; i < arr.length(); i++) result.add(jsonToListOfOrbitList(arr.getJSONArray(i)));
        return result;
    }

    // =========================================================================
    // Double list variants
    // =========================================================================

    public static JSONArray doubleListToJson(List<Double> list) {
        JSONArray arr = new JSONArray();
        for (double v : list) arr.put(v);
        return arr;
    }

    public static List<Double> jsonToDoubleList(JSONArray arr) {
        List<Double> list = new ArrayList<>();
        for (int i = 0; i < arr.length(); i++) list.add(arr.getDouble(i));
        return list;
    }

    static JSONArray listOfDoubleListToJson(List<List<Double>> data) {
        JSONArray outer = new JSONArray();
        for (List<Double> inner : data) outer.put(doubleListToJson(inner));
        return outer;
    }

    static List<List<Double>> jsonToListOfDoubleList(JSONArray arr) {
        List<List<Double>> result = new ArrayList<>();
        for (int i = 0; i < arr.length(); i++) result.add(jsonToDoubleList(arr.getJSONArray(i)));
        return result;
    }

    static JSONArray list3DDoubleToJson(List<List<List<Double>>> data) {
        JSONArray outer = new JSONArray();
        for (List<List<Double>> d : data) outer.put(listOfDoubleListToJson(d));
        return outer;
    }

    static List<List<List<Double>>> jsonToList3DDouble(JSONArray arr) {
        List<List<List<Double>>> result = new ArrayList<>();
        for (int i = 0; i < arr.length(); i++) result.add(jsonToListOfDoubleList(arr.getJSONArray(i)));
        return result;
    }

    // =========================================================================
    // Integer list variants
    // =========================================================================

    public static JSONArray intListListToJson(List<List<Integer>> list) {
        JSONArray outer = new JSONArray();
        for (List<Integer> inner : list) {
            JSONArray ia = new JSONArray();
            for (int v : inner) ia.put(v);
            outer.put(ia);
        }
        return outer;
    }

    public static List<List<Integer>> jsonToIntListList(JSONArray arr) {
        List<List<Integer>> list = new ArrayList<>();
        for (int i = 0; i < arr.length(); i++) {
            JSONArray inner = arr.getJSONArray(i);
            List<Integer> row = new ArrayList<>();
            for (int j = 0; j < inner.length(); j++) row.add(inner.getInt(j));
            list.add(row);
        }
        return list;
    }

    static JSONArray listOfIntListListToJson(List<List<List<Integer>>> data) {
        JSONArray outer = new JSONArray();
        for (List<List<Integer>> inner : data) outer.put(intListListToJson(inner));
        return outer;
    }

    static List<List<List<Integer>>> jsonToListOfIntListList(JSONArray arr) {
        List<List<List<Integer>>> result = new ArrayList<>();
        for (int i = 0; i < arr.length(); i++) result.add(jsonToIntListList(arr.getJSONArray(i)));
        return result;
    }

    static JSONArray list4DIntToJson(List<List<List<List<Integer>>>> data) {
        JSONArray outer = new JSONArray();
        for (List<List<List<Integer>>> d : data) outer.put(listOfIntListListToJson(d));
        return outer;
    }

    static List<List<List<List<Integer>>>> jsonToList4DInt(JSONArray arr) {
        List<List<List<List<Integer>>>> result = new ArrayList<>();
        for (int i = 0; i < arr.length(); i++) result.add(jsonToListOfIntListList(arr.getJSONArray(i)));
        return result;
    }
}

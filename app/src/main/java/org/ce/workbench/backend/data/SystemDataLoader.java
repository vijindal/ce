package org.ce.workbench.backend.data;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Optional;

/**
 * Loader for system data with separated storage:
 * - CECs: Element-specific, stored in /data/systems/{Elements}/cec.json
 * - Model data: Shared cluster/CF data, stored in /data/models/{Structure}_{Phase}_{Model}/model_data.json
 * 
 * This separation allows multiple alloy systems (Ti-Nb, Ti-V, Ti-Zr) to share the same 
 * cluster/CF data (BCC_A2_T) while having different CECs.
 */
public class SystemDataLoader {
    
    private static final String SYSTEMS_BASE_PATH = "/data/systems/";
    private static final String MODELS_BASE_PATH = "/data/models/";
    private static final String CEC_FILE = "cec.json";
    private static final String MODEL_FILE = "model_data.json";
    
    /**
     * Checks if CEC data exists for the given elements.
     * @param elements e.g., "Ti-Nb"
     * @return true if cec.json exists
     */
    public static boolean cecExists(String elements) {
        return resourceExists(SYSTEMS_BASE_PATH + elements + "/" + CEC_FILE);
    }
    
    /**
     * Checks if model data exists for the given structure/phase/model.
     * @param structure e.g., "BCC"
     * @param phase e.g., "A2"
     * @param model e.g., "T"
     * @return true if model_data.json exists
     */
    public static boolean modelDataExists(String structure, String phase, String model) {
        String modelId = structure + "_" + phase + "_" + model;
        return resourceExists(MODELS_BASE_PATH + modelId + "/" + MODEL_FILE);
    }
    
    /**
     * Checks if cluster result data exists (part of model data).
     */
    public static boolean clusterResultExists(String structure, String phase, String model) {
        return modelDataExists(structure, phase, model);
    }
    
    /**
     * Checks if CF result data exists (part of model data).
     */
    public static boolean cfResultExists(String structure, String phase, String model) {
        return modelDataExists(structure, phase, model);
    }
    
    /**
     * Loads CEC values for given elements.
     * @param elements e.g., "Ti-Nb"
     * @return Optional containing CEC data, or empty if not found
     */
    public static Optional<CECData> loadCecData(String elements) {
        try {
            String json = loadResourceAsString(SYSTEMS_BASE_PATH + elements + "/" + CEC_FILE);
            if (json == null) {
                return Optional.empty();
            }
            
            JSONObject obj = new JSONObject(json);
            CECData data = new CECData();
            
            data.elements = obj.getString("elements");
            
            if (obj.has("cecValues")) {
                JSONArray values = obj.getJSONArray("cecValues");
                data.cecValues = new double[values.length()];
                for (int i = 0; i < values.length(); i++) {
                    data.cecValues[i] = values.getDouble(i);
                }
            }
            
            if (obj.has("cecUnits")) {
                data.cecUnits = obj.getString("cecUnits");
            }
            
            if (obj.has("reference")) {
                data.reference = obj.getString("reference");
            }
            
            return Optional.of(data);
        } catch (Exception e) {
            System.err.println("Error loading CEC data for " + elements + ": " + e.getMessage());
            return Optional.empty();
        }
    }
    
    /**
     * Loads model data (cluster/CF metadata) for given structure/phase/model.
     * @param structure e.g., "BCC"
     * @param phase e.g., "A2"
     * @param model e.g., "T"
     * @return Optional containing model data, or empty if not found
     */
    public static Optional<ModelData> loadModelData(String structure, String phase, String model) {
        try {
            String modelId = structure + "_" + phase + "_" + model;
            String json = loadResourceAsString(MODELS_BASE_PATH + modelId + "/" + MODEL_FILE);
            if (json == null) {
                return Optional.empty();
            }
            
            JSONObject obj = new JSONObject(json);
            ModelData data = new ModelData();
            
            data.modelId = obj.getString("modelId");
            data.structure = obj.getString("structure");
            data.phase = obj.getString("phase");
            data.model = obj.getString("model");
            
            if (obj.has("description")) {
                data.description = obj.getString("description");
            }
            
            // Load cluster data
            if (obj.has("clusterData")) {
                JSONObject clusterObj = obj.getJSONObject("clusterData");
                data.clusterData = new ClusterResultMetadata(
                    modelId,
                    clusterObj.getInt("tcdis"),
                    clusterObj.getInt("nxcdis"),
                    clusterObj.getInt("tc"),
                    clusterObj.getInt("nxc"),
                    clusterObj.getString("clusterFile"),
                    clusterObj.getString("symmetryGroup")
                );
            }
            
            // Load CF data
            if (obj.has("cfData")) {
                JSONObject cfObj = obj.getJSONObject("cfData");
                data.cfData = new CFResultMetadata(
                    modelId,
                    cfObj.getInt("tcf"),
                    cfObj.getInt("nxcf"),
                    cfObj.getInt("ncf"),
                    cfObj.getInt("tcfdis"),
                    cfObj.getInt("numComponents")
                );
            }
            
            // Load transformation matrix
            if (obj.has("transformationMatrix")) {
                JSONArray matrixArray = obj.getJSONArray("transformationMatrix");
                data.transformationMatrix = new double[3][3];
                for (int i = 0; i < 3; i++) {
                    JSONArray row = matrixArray.getJSONArray(i);
                    for (int j = 0; j < 3; j++) {
                        data.transformationMatrix[i][j] = row.getDouble(j);
                    }
                }
            }
            
            // Load translation vector
            if (obj.has("translationVector")) {
                JSONArray vecArray = obj.getJSONArray("translationVector");
                data.translationVector = new double[3];
                for (int i = 0; i < 3; i++) {
                    data.translationVector[i] = vecArray.getDouble(i);
                }
            }
            
            return Optional.of(data);
        } catch (Exception e) {
            System.err.println("Error loading model data for " + structure + "_" + phase + "_" + model + ": " + e.getMessage());
            return Optional.empty();
        }
    }
    
    /**
     * Loads CEC values only (convenience method).
     * @param elements e.g., "Ti-Nb"
     * @return Optional containing CEC values array, or empty if not found
     */
    public static Optional<double[]> loadCecValues(String elements) {
        Optional<CECData> data = loadCecData(elements);
        return data.map(cecData -> cecData.cecValues);
    }
    
    /**
     * Loads cluster result metadata from model data.
     */
    public static Optional<ClusterResultMetadata> loadClusterResult(String structure, String phase, String model) {
        Optional<ModelData> data = loadModelData(structure, phase, model);
        return data.map(modelData -> modelData.clusterData);
    }
    
    /**
     * Loads CF result metadata from model data.
     */
    public static Optional<CFResultMetadata> loadCFResult(String structure, String phase, String model) {
        Optional<ModelData> data = loadModelData(structure, phase, model);
        return data.map(modelData -> modelData.cfData);
    }
    
    /**
     * Saves CEC data to external directory.
     * @param cecData CEC data to save
     * @param externalDataPath path to external data directory
     */
    public static void saveCecData(CECData cecData, Path externalDataPath) {
        try {
            Path systemDir = externalDataPath.resolve("systems").resolve(cecData.elements);
            Files.createDirectories(systemDir);
            
            JSONObject obj = new JSONObject();
            obj.put("elements", cecData.elements);
            
            if (cecData.cecValues != null) {
                JSONArray arr = new JSONArray();
                for (double val : cecData.cecValues) {
                    arr.put(val);
                }
                obj.put("cecValues", arr);
            }
            
            if (cecData.cecUnits != null) {
                obj.put("cecUnits", cecData.cecUnits);
            }
            
            if (cecData.reference != null) {
                obj.put("reference", cecData.reference);
            }
            
            Path cecFile = systemDir.resolve(CEC_FILE);
            Files.writeString(cecFile, obj.toString(2), StandardCharsets.UTF_8);
            
            System.out.println("Saved CEC data to " + cecFile);
        } catch (IOException e) {
            System.err.println("Error saving CEC data: " + e.getMessage());
        }
    }
    
    /**
     * Saves model data to external directory.
     * @param modelData model data to save
     * @param externalDataPath path to external data directory
     */
    public static void saveModelData(ModelData modelData, Path externalDataPath) {
        try {
            Path modelDir = externalDataPath.resolve("models").resolve(modelData.modelId);
            Files.createDirectories(modelDir);
            
            JSONObject obj = new JSONObject();
            obj.put("modelId", modelData.modelId);
            obj.put("structure", modelData.structure);
            obj.put("phase", modelData.phase);
            obj.put("model", modelData.model);
            
            if (modelData.description != null) {
                obj.put("description", modelData.description);
            }
            
            if (modelData.clusterData != null) {
                JSONObject clusterObj = new JSONObject();
                clusterObj.put("tcdis", modelData.clusterData.tcdis);
                clusterObj.put("nxcdis", modelData.clusterData.nxcdis);
                clusterObj.put("tc", modelData.clusterData.tc);
                clusterObj.put("nxc", modelData.clusterData.nxc);
                clusterObj.put("clusterFile", modelData.clusterData.clusterFile);
                clusterObj.put("symmetryGroup", modelData.clusterData.symmetryGroup);
                obj.put("clusterData", clusterObj);
            }
            
            if (modelData.cfData != null) {
                JSONObject cfObj = new JSONObject();
                cfObj.put("tcf", modelData.cfData.tcf);
                cfObj.put("nxcf", modelData.cfData.nxcf);
                cfObj.put("ncf", modelData.cfData.ncf);
                cfObj.put("tcfdis", modelData.cfData.tcfdis);
                cfObj.put("numComponents", modelData.cfData.numComponents);
                obj.put("cfData", cfObj);
            }
            
            Path modelFile = modelDir.resolve(MODEL_FILE);
            Files.writeString(modelFile, obj.toString(2), StandardCharsets.UTF_8);
            
            System.out.println("Saved model data to " + modelFile);
        } catch (IOException e) {
            System.err.println("Error saving model data: " + e.getMessage());
        }
    }
    
    // -------------------------------------------------------------------------
    // Helper methods
    // -------------------------------------------------------------------------
    
    private static boolean resourceExists(String resourcePath) {
        InputStream is = SystemDataLoader.class.getResourceAsStream(resourcePath);
        if (is != null) {
            try {
                is.close();
            } catch (IOException ignored) {}
            return true;
        }
        return false;
    }
    
    private static String loadResourceAsString(String resourcePath) {
        try (InputStream is = SystemDataLoader.class.getResourceAsStream(resourcePath)) {
            if (is == null) {
                return null;
            }
            return new String(is.readAllBytes(), StandardCharsets.UTF_8);
        } catch (IOException e) {
            System.err.println("Error reading resource " + resourcePath + ": " + e.getMessage());
            return null;
        }
    }
    
    // -------------------------------------------------------------------------
    // Data classes
    // -------------------------------------------------------------------------
    
    /**
     * Element-specific CEC data.
     */
    public static class CECData {
        public String elements;
        public double[] cecValues;
        public String cecUnits;
        public String reference;
    }
    
    /**
     * Model-specific cluster/CF data (shared across alloys).
     */
    public static class ModelData {
        public String modelId;
        public String structure;
        public String phase;
        public String model;
        public String description;
        public ClusterResultMetadata clusterData;
        public CFResultMetadata cfData;
        public double[][] transformationMatrix;
        public double[] translationVector;
    }
    
    public static class ClusterResultMetadata {
        public final String systemId;
        public final int tcdis;
        public final int nxcdis;
        public final int tc;
        public final int nxc;
        public final String clusterFile;
        public final String symmetryGroup;
        
        public ClusterResultMetadata(String systemId, int tcdis, int nxcdis, int tc, int nxc,
                                    String clusterFile, String symmetryGroup) {
            this.systemId = systemId;
            this.tcdis = tcdis;
            this.nxcdis = nxcdis;
            this.tc = tc;
            this.nxc = nxc;
            this.clusterFile = clusterFile;
            this.symmetryGroup = symmetryGroup;
        }
        
        @Override
        public String toString() {
            return "ClusterResult{tcdis=" + tcdis + ", tc=" + tc + ", file=" + clusterFile + "}";
        }
    }
    
    public static class CFResultMetadata {
        public final String systemId;
        public final int tcf;
        public final int nxcf;
        public final int ncf;
        public final int tcfdis;
        public final int numComponents;
        
        public CFResultMetadata(String systemId, int tcf, int nxcf, int ncf, int tcfdis, int numComponents) {
            this.systemId = systemId;
            this.tcf = tcf;
            this.nxcf = nxcf;
            this.ncf = ncf;
            this.tcfdis = tcfdis;
            this.numComponents = numComponents;
        }
        
        @Override
        public String toString() {
            return "CFResult{tcf=" + tcf + ", nxcf=" + nxcf + ", numComp=" + numComponents + "}";
        }
    }
}

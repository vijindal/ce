package org.ce.workbench.util.mcs;

import java.util.HashMap;
import java.util.Map;

/**
 * Utility for mapping structure/phase combinations to symmetry groups and cluster files.
 * 
 * Structure and phase are interchangeable terms used as a combined specification:
 * - BCC_A2, FCC_A1, BCC_B2, etc.
 * 
 * Symmetry groups: A2-SG, A1-SG, B2-SG, etc. (derived from phase component)
 * Cluster files: cluster/A2-T.txt, cluster/A1-T.txt, etc. (phase_model.txt)
 */
public class StructureModelMapping {
    
    private static final Map<String, String> SYMMETRY_GROUP_MAPPING = new HashMap<>();
    private static final Map<String, String> PHASE_TO_STRUCTURE_MAPPING = new HashMap<>();
    
    static {
        // Initialize symmetry group mappings
        // Phase → Symmetry group name (without -SG suffix in keys, added in resolution)
        PHASE_TO_STRUCTURE_MAPPING.put("A1", "A1");    // FCC_A1 → A1-SG
        PHASE_TO_STRUCTURE_MAPPING.put("A2", "A2");    // BCC_A2 → A2-SG
        PHASE_TO_STRUCTURE_MAPPING.put("A3", "A3");    // HCP_A3 → A3-SG
        PHASE_TO_STRUCTURE_MAPPING.put("B2", "B2");    // BCC_B2 → B2-SG
        PHASE_TO_STRUCTURE_MAPPING.put("B3", "B3");    // ZnS structure → B3-SG
        PHASE_TO_STRUCTURE_MAPPING.put("L1_2", "L1_2");  // FCC_L1_2 → L1_2-SG
        PHASE_TO_STRUCTURE_MAPPING.put("L1_0", "L1_0");  // FCC_L1_0 → L1_0-SG
        PHASE_TO_STRUCTURE_MAPPING.put("D0_22", "D0_22"); // BCC_D0_22 → D0_22-SG
        
        // Populate full symmetry group mapping if needed for reverse lookups
        for (String phase : PHASE_TO_STRUCTURE_MAPPING.keySet()) {
            SYMMETRY_GROUP_MAPPING.put(phase, PHASE_TO_STRUCTURE_MAPPING.get(phase) + "-SG");
        }
    }
    
    /**
     * Resolves the symmetry group file name from a structure/phase specification.
     * 
     * @param structurePhase Combined specification like "BCC_A2", "FCC_A1", etc.
     * @return The symmetry group base name (e.g., "A2-SG", "A1-SG")
     * @throws IllegalArgumentException if structure/phase format is invalid
     */
    public static String resolveSymmetryGroup(String structurePhase) {
        String[] parts = structurePhase.split("_");
        if (parts.length < 2) {
            throw new IllegalArgumentException("Invalid structure/phase format: " + structurePhase);
        }
        
        String phase = parts[1];
        String symmetryGroup = PHASE_TO_STRUCTURE_MAPPING.get(phase);
        
        if (symmetryGroup == null) {
            throw new IllegalArgumentException("Unknown phase: " + phase);
        }
        
        return symmetryGroup + "-SG";
    }
    
    /**
     * Resolves the cluster file path from a structure/phase and model specification.
     * 
     * @param structurePhase Combined specification like "BCC_A2", "FCC_A1", etc.
     * @param model CVM approximation model (e.g., "T" for tetrahedron, "P" for pair)
     * @return The cluster file path (e.g., "cluster/A2-T.txt", "cluster/A1-P.txt")
     * @throws IllegalArgumentException if structure/phase format is invalid
     */
    public static String resolveClusterFile(String structurePhase, String model) {
        String[] parts = structurePhase.split("_");
        if (parts.length < 2) {
            throw new IllegalArgumentException("Invalid structure/phase format: " + structurePhase);
        }
        
        String phase = parts[1];
        String phase_mapped = PHASE_TO_STRUCTURE_MAPPING.get(phase);
        
        if (phase_mapped == null) {
            throw new IllegalArgumentException("Unknown phase: " + phase);
        }
        
        // Construct cluster file name: cluster/{Phase}-{Model}.txt
        return "cluster/" + phase_mapped + "-" + model + ".txt";
    }
    
    /**
     * Resolves the cluster file base name (without extension) for resource lookup.
     * 
     * @param structurePhase Combined specification like "BCC_A2", "FCC_A1", etc.
     * @param model CVM approximation model
     * @return The cluster file base name (e.g., "A2-T", "A1-P")
     */
    public static String resolveClusterFileBaseName(String structurePhase, String model) {
        String[] parts = structurePhase.split("_");
        if (parts.length < 2) {
            throw new IllegalArgumentException("Invalid structure/phase format: " + structurePhase);
        }
        
        String phase = parts[1];
        String phase_mapped = PHASE_TO_STRUCTURE_MAPPING.get(phase);
        
        if (phase_mapped == null) {
            throw new IllegalArgumentException("Unknown phase: " + phase);
        }
        
        return phase_mapped + "-" + model;
    }
    
    /**
     * Validates that a structure/phase specification has the correct format.
     * 
     * @param structurePhase The specification to validate
     * @return true if valid, false otherwise
     */
    public static boolean isValidStructurePhase(String structurePhase) {
        String[] parts = structurePhase.split("_");
        if (parts.length != 2) {
            return false;
        }
        
        String phase = parts[1];
        return PHASE_TO_STRUCTURE_MAPPING.containsKey(phase);
    }
    
    /**
     * Gets all registered phase types.
     * 
     * @return Array of known phase types
     */
    public static String[] getKnownPhases() {
        return PHASE_TO_STRUCTURE_MAPPING.keySet().toArray(new String[0]);
    }
}

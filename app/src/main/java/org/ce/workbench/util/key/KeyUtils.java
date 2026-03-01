package org.ce.workbench.util.key;

import org.ce.workbench.gui.model.SystemInfo;

/**
 * Shared utility for computing the various string keys used across the
 * workbench: system IDs, CEC keys, and cluster-cache keys.
 *
 * <p>Key terminology:</p>
 * <ul>
 *   <li><b>systemId</b>  — uniquely identifies a system instance
 *       ({@code "Ti-Nb_BCC_A2_T"})</li>
 *   <li><b>cecKey</b>    — element + model; one CEC file per alloy+model
 *       ({@code "Ti-Nb_BCC_A2_T"})</li>
 *   <li><b>clusterKey</b> — component-count + model; topology shared across
 *       all alloys with same structure ({@code "BCC_A2_T_bin"})</li>
 * </ul>
 */
public final class KeyUtils {

    private KeyUtils() {} // utility class

    // =========================================================================
    // Component suffix
    // =========================================================================

    /**
     * Maps a component count to the human-readable suffix used in cache keys.
     *
     * @param numComponents number of chemical components (2–5 typical)
     * @return suffix string, e.g. {@code "bin"}, {@code "tern"}, {@code "comp6"}
     */
    public static String componentSuffix(int numComponents) {
        switch (numComponents) {
            case 2:  return "bin";
            case 3:  return "tern";
            case 4:  return "quat";
            case 5:  return "quint";
            default: return "comp" + numComponents;
        }
    }

    // =========================================================================
    // Key builders from raw strings
    // =========================================================================

    /**
     * Builds the CEC key: {@code elements_structure_phase_model}.
     *
     * @param elements  dash-separated element string, e.g. {@code "Ti-Nb"}
     * @param structure e.g. {@code "BCC"}
     * @param phase     e.g. {@code "A2"}
     * @param model     e.g. {@code "T"}
     * @return e.g. {@code "Ti-Nb_BCC_A2_T"}
     */
    public static String cecKey(String elements, String structure, String phase, String model) {
        return elements + "_" + structure + "_" + phase + "_" + model;
    }

    /**
     * Builds the cluster-cache key: {@code structure_phase_model_compSuffix}.
     *
     * @param structure     e.g. {@code "BCC"}
     * @param phase         e.g. {@code "A2"}
     * @param model         e.g. {@code "T"}
     * @param numComponents number of chemical components
     * @return e.g. {@code "BCC_A2_T_bin"}
     */
    public static String clusterKey(String structure, String phase, String model, int numComponents) {
        return structure + "_" + phase + "_" + model + "_" + componentSuffix(numComponents);
    }

    // =========================================================================
    // Key builders from SystemInfo
    // =========================================================================

    /**
     * Derives the CEC key from a {@link SystemInfo} and its element list.
     *
     * @param system the system instance
     * @return e.g. {@code "Ti-Nb_BCC_A2_T"}
     */
    public static String cecKey(SystemInfo system) {
        String elements = String.join("-", system.getComponents());
        return cecKey(elements, system.getStructure(), system.getPhase(), system.getModel());
    }

    /**
     * Derives the cluster-cache key from a {@link SystemInfo}.
     *
     * @param system the system instance
     * @return e.g. {@code "BCC_A2_T_bin"}
     */
    public static String clusterKey(SystemInfo system) {
        return clusterKey(system.getStructure(), system.getPhase(),
                          system.getModel(), system.getNumComponents());
    }
}

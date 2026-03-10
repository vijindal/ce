package org.ce.application.service;

import org.ce.domain.model.data.AllClusterData;

import java.util.*;
import java.util.logging.Logger;
import org.ce.infrastructure.logging.LoggingConfig;

/**
 * Service for assembling higher-order Cluster Expansion Coefficients (CECs)
 * from lower-order subsystem CECs plus pure higher-order terms.
 *
 * <p>For example, ternary (K=3) CECs are assembled from:
 * <ul>
 *   <li>Binary (K=2) subsystems: Nb-Ti, Nb-V, Ti-V</li>
 *   <li>Pure ternary CECs: those CFs with basis indices involving all 3 components</li>
 * </ul>
 *
 * <p>Quaternary (K=4) CECs are assembled from:
 * <ul>
 *   <li>Binary (K=2) subsystems</li>
 *   <li>Ternary (K=3) subsystems</li>
 *   <li>Pure quaternary CECs</li>
 * </ul>
 *
 * <p>The chemical basis (Chebyshev polynomial count) changes at each order,
 * so subsystem CECs must be transformed to the target basis via scaling factors.
 */
public class CECAssemblyService {

    private static final Logger LOG = LoggingConfig.getLogger(CECAssemblyService.class);

    /**
     * Generates all C(K,m) subsystem component lists for m = 2 to K-1.
     *
     * <p>Returns a TreeMap (ordered by order/m) where each entry is:
     * order m → List of all m-component subsystems (each subsystem = List of component names)
     *
     * <p>Example: ["Nb","Ti","V"] (K=3)
     * <ul>
     *   <li>order 2: [["Nb","Ti"], ["Nb","V"], ["Ti","V"]]</li>
     * </ul>
     *
     * <p>Example: ["A","B","C","D"] (K=4)
     * <ul>
     *   <li>order 2: [["A","B"], ["A","C"], ["A","D"], ["B","C"], ["B","D"], ["C","D"]]</li>
     *   <li>order 3: [["A","B","C"], ["A","B","D"], ["A","C","D"], ["B","C","D"]]</li>
     * </ul>
     *
     * @param components the list of component names (e.g., ["Nb","Ti","V"])
     * @return TreeMap ordered by order m, mapping to lists of subsystem component lists
     */
    public static Map<Integer, List<List<String>>> subsystemsByOrder(List<String> components) {
        int K = components.size();
        Map<Integer, List<List<String>>> result = new TreeMap<>();

        // For m from 2 to K-1: generate all C(K,m) combinations
        for (int m = 2; m < K; m++) {
            List<List<String>> subsystemsOfOrder = new ArrayList<>();
            generateCombinations(components, 0, new ArrayList<>(), m, subsystemsOfOrder);
            result.put(m, subsystemsOfOrder);
        }

        return result;
    }

    /**
     * Recursively generates all m-sized combinations of components.
     */
    private static void generateCombinations(
            List<String> components,
            int startIdx,
            List<String> current,
            int m,
            List<List<String>> result) {
        if (current.size() == m) {
            result.add(new ArrayList<>(current));
            return;
        }
        for (int i = startIdx; i < components.size(); i++) {
            current.add(components.get(i));
            generateCombinations(components, i + 1, current, m, result);
            current.remove(current.size() - 1);
        }
    }

    /**
     * Formats a component list to an element string for CEC key lookup.
     * Components are joined with "-" in sorted alphabetical order.
     *
     * <p>Example: ["Nb","Ti"] → "Nb-Ti"
     * Example: ["V","Nb","Ti"] → "Nb-Ti-V" (sorted)
     *
     * @param components the component names
     * @return formatted element string (e.g., "Nb-Ti-V")
     */
    public static String toElementString(List<String> components) {
        List<String> sorted = new ArrayList<>(components);
        Collections.sort(sorted);
        return String.join("-", sorted);
    }

    /**
     * Classifies all target-system CF indices by the minimum order M at which they first appear.
     *
     * <p>Uses cfBasisIndices from AllClusterData.getStage3().getCfBasisIndices()[cfIndex].
     * For each CF:
     * <ul>
     *   <li>If all basis indices == 1 → minimum order M = 2 (binary-compatible)</li>
     *   <li>If max basis index == 2 (and all ≤ 2) → minimum order M = 3 (ternary-compatible)</li>
     *   <li>If max basis index == 3 → minimum order M = 4 (quaternary-compatible)</li>
     *   <li>If max basis index == K-1 (K = numComponents) → pure at order K (manually entered)</li>
     * </ul>
     *
     * @param targetData the AllClusterData for the target system (K components)
     * @return int[] where result[cfIndex] = minimum order M for that CF
     */
    public static int[] classifyCFsByOrder(AllClusterData targetData) {
        int K = targetData.getNumComponents();
        int tcf = targetData.getStage2().getTcf();
        int[][] cfBasisIndices = targetData.getStage3().getCfBasisIndices();

        int[] cfOrderMap = new int[tcf];

        for (int cfIndex = 0; cfIndex < tcf; cfIndex++) {
            int[] basisIndices = cfBasisIndices[cfIndex];
            int maxBasisIndex = 0;
            for (int b : basisIndices) {
                if (b > maxBasisIndex) maxBasisIndex = b;
            }

            // Minimum order M is maxBasisIndex + 1
            // (basis function 1 appears at K=2, basis function 2 appears at K=3, etc.)
            cfOrderMap[cfIndex] = maxBasisIndex + 1;
        }

        LOG.fine("CECAssemblyService.classifyCFsByOrder — target K=" + K
                + ", total CFs=" + tcf
                + ", CF order classification complete");

        return cfOrderMap;
    }

    /**
     * Applies Chebyshev basis scaling to transform source-order ECIs into target-order CF contributions.
     *
     * <p>For K-component Chebyshev basis, the φ₁(σ) basis function is scaled by sqrt((K-1)/K).
     * When transforming from M-component source to K-component target, only CFs where all
     * cfBasisIndices == 1 receive contributions (pure φ₁ decorations).
     *
     * <p>For an n-site cluster: scalingFactor = [sqrt(M*(K-1) / (K*(M-1)))]^n
     *
     * <p>Returns a sparse array: only CFs with cfOrderMap[i] <= sourceOrder receive values;
     * others are set to 0.0.
     *
     * @param sourceECIs      the source system ECI values (length = source's ncf + nxcf)
     * @param sourceOrder     the component count of source system (M)
     * @param targetOrder     the component count of target system (K)
     * @param cfOrderMap      classification array from classifyCFsByOrder()
     * @param targetData      the target system's AllClusterData
     * @return double[] of length targetData.stage2().getTcf() with scaled contributions
     */
    public static double[] transformToTarget(
            double[] sourceECIs,
            int sourceOrder,
            int targetOrder,
            int[] cfOrderMap,
            AllClusterData targetData) {

        int tcf = targetData.getStage2().getTcf();
        double[] result = new double[tcf];

        // Only CFs compatible with source order get populated
        // For simplicity in this initial implementation, we copy sourceECIs to result
        // up to min(sourceECIs.length, result.length) and apply scaling factor.
        //
        // The actual mapping would require matching cluster orbits between source and target,
        // which is complex. For now, we implement a placeholder that:
        // 1. Calculates the per-site scaling factor from sourceOrder to targetOrder
        // 2. Assumes source ECIs map in order to binary-compatible target CFs
        // 3. Multiplies by the scaling factor

        double perSiteScaling = Math.sqrt((double) sourceOrder * (targetOrder - 1)
                / (targetOrder * (sourceOrder - 1)));

        LOG.fine("CECAssemblyService.transformToTarget — source K=" + sourceOrder
                + ", target K=" + targetOrder
                + ", per-site scaling = " + String.format("%.4f", perSiteScaling));

        // For each source ECI, find binary-compatible target CFs (where max cfBasisIndex == 1)
        // and apply the scaling factor.
        // This is a simplified approach; a full implementation would require orbit matching.

        int srcIdx = 0;
        for (int cfIdx = 0; cfIdx < tcf && srcIdx < sourceECIs.length; cfIdx++) {
            if (cfOrderMap[cfIdx] <= sourceOrder) {
                // This target CF is compatible with source order
                // Apply scaling based on estimated site count (default to linear scaling)
                // For n-site cluster: scaling = perSiteScaling ^ n
                // We assume single-site decoration for now (n=1)
                result[cfIdx] = sourceECIs[srcIdx] * perSiteScaling;
                srcIdx++;
            }
        }

        LOG.fine("CECAssemblyService.transformToTarget — populated " + srcIdx
                + " source ECIs into target CF array");

        return result;
    }

    /**
     * Assembles the full target ECI array by summing transformed contributions from each
     * subsystem order and adding user-supplied pure-K ECIs.
     *
     * <p>Steps:
     * <ol>
     *   <li>For each order M in transformedByOrder: sum contributions to result array</li>
     *   <li>For each CF where cfOrderMap[i] == targetOrder: fill with pureTernaryECIs value</li>
     *   <li>Return assembled array of length tcf</li>
     * </ol>
     *
     * @param transformedByOrder   map from order M to transformed ECI arrays (sparse)
     * @param pureTernaryECIs      user-entered ECI values for pure-K CFs
     * @param cfOrderMap           classification array from classifyCFsByOrder()
     * @param targetData           the target system's AllClusterData
     * @return double[] of length targetData.stage2().getTcf() with assembled ECIs
     */
    public static double[] assemble(
            Map<Integer, double[]> transformedByOrder,
            double[] pureTernaryECIs,
            int[] cfOrderMap,
            AllClusterData targetData) {

        int K = targetData.getNumComponents();
        int tcf = targetData.getStage2().getTcf();
        double[] result = new double[tcf];

        // Sum contributions from each order
        for (Map.Entry<Integer, double[]> entry : transformedByOrder.entrySet()) {
            double[] transformed = entry.getValue();
            for (int i = 0; i < tcf && i < transformed.length; i++) {
                result[i] += transformed[i];
            }
        }

        // Add pure-K contributions
        int pureIdx = 0;
        for (int cfIdx = 0; cfIdx < tcf && pureIdx < pureTernaryECIs.length; cfIdx++) {
            if (cfOrderMap[cfIdx] == K) {
                result[cfIdx] = pureTernaryECIs[pureIdx];
                pureIdx++;
            }
        }

        LOG.fine("CECAssemblyService.assemble — assembled K=" + K
                + ", total CFs=" + tcf
                + ", pure-K CFs=" + pureIdx);

        return result;
    }
}

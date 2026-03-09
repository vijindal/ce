package org.ce.infrastructure.persistence.migration;

import org.json.JSONArray;
import org.json.JSONObject;

/**
 * Infers missing stage3.cfBasisIndices for legacy cache payloads.
 */
final class LegacyStage3CfBasisIndicesInferer {

    private LegacyStage3CfBasisIndicesInferer() {
    }

    static int[][] inferFromStage2(JSONObject stage2, int numComponents) {
        if (stage2 == null || !stage2.has("disCFData")) {
            throw new IllegalStateException(
                    "Legacy stage3 payload missing cfBasisIndices and stage2 data unavailable for inference.");
        }

        int tcf = stage2.getInt("tcf");
        int ncf = stage2.getInt("ncf");
        int nxcf = tcf - ncf;

        if (numComponents != 2 || nxcf != 1) {
            throw new IllegalStateException(
                    "Legacy stage3 payload missing cfBasisIndices for " + numComponents
                            + "-component system. Re-run identification to regenerate cache.");
        }

        JSONArray cfClusters = stage2
                .getJSONObject("disCFData")
                .getJSONArray("clusCoordList");

        int[][] inferred = new int[tcf][];

        // Binary fallback: each site contributes sigma^1; rank = number of sites in CF cluster.
        for (int col = 0; col < ncf; col++) {
            int rank = 1;
            if (col < cfClusters.length()) {
                rank = Math.max(1, countClusterSites(cfClusters.getJSONObject(col)));
            }
            int[] indices = new int[rank];
            for (int i = 0; i < rank; i++) {
                indices[i] = 1;
            }
            inferred[col] = indices;
        }

        // Binary systems have one point-CF basis index: sigma^1.
        for (int col = ncf; col < tcf; col++) {
            inferred[col] = new int[]{1};
        }

        return inferred;
    }

    private static int countClusterSites(JSONObject cluster) {
        int count = 0;
        JSONArray sublattices = cluster.getJSONArray("sublattices");
        for (int i = 0; i < sublattices.length(); i++) {
            JSONObject sub = sublattices.getJSONObject(i);
            count += sub.getJSONArray("sites").length();
        }
        return count;
    }
}


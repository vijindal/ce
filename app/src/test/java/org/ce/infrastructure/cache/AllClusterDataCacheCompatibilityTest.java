package org.ce.infrastructure.cache;

import org.ce.domain.model.data.AllClusterData;
import org.ce.infrastructure.persistence.migration.ClusterCacheSchemaMigrator;
import org.json.JSONObject;
import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AllClusterDataCacheCompatibilityTest {

    @Test
    void migrate_then_deserialize_legacy_stage3_without_cfBasisIndices() throws Exception {
        Path file = findProjectRoot()
            .resolve("data")
            .resolve("cluster_cache")
            .resolve("BCC_A2_T_bin")
            .resolve("all_cluster_data.json");
        String raw = Files.readString(file);
        JSONObject root = new JSONObject(raw);

        // Force legacy-path behavior regardless of on-disk schema version.
        root.getJSONObject("stage3").remove("cfBasisIndices");
        root.remove("schemaVersion");

        boolean migrated = ClusterCacheSchemaMigrator.migrateRootInMemory(root);
        assertTrue(migrated, "Expected migrator to upgrade legacy payload");

        AllClusterData data = AllClusterDataCache.deserialize(root);
        assertNotNull(data.getStage3());
        assertNotNull(data.getStage3().getCfBasisIndices());

        int tcf = data.getStage2().getTcf();
        int ncf = data.getStage2().getNcf();
        int[][] cfBasisIndices = data.getStage3().getCfBasisIndices();

        assertEquals(tcf, cfBasisIndices.length, "cfBasisIndices length should match tcf");
        for (int col = ncf; col < tcf; col++) {
            assertEquals(1, cfBasisIndices[col].length, "point CF should have rank 1");
            assertEquals(1, cfBasisIndices[col][0], "binary point CF should be sigma^1");
        }
    }

    @Test
    void load_binary_cluster_cache_does_not_fail_when_legacy_stage3_missing_cfBasisIndices() throws Exception {
        Optional<AllClusterData> loaded = AllClusterDataCache.load("BCC_A2_T_bin");
        assertTrue(loaded.isPresent(), "Expected BCC_A2_T_bin cache to load");
        assertNotNull(loaded.get().getStage3(), "Expected stage3 in loaded cache");
        assertNotNull(loaded.get().getStage3().getCfBasisIndices(), "cfBasisIndices should be present after load");
    }

    private static Path findProjectRoot() {
        Path current = Path.of(System.getProperty("user.dir"));
        while (current != null) {
            if (Files.exists(current.resolve("app").resolve("build.gradle"))) {
                return current;
            }
            current = current.getParent();
        }
        throw new IllegalStateException("Cannot locate project root from user.dir=" + System.getProperty("user.dir"));
    }
}


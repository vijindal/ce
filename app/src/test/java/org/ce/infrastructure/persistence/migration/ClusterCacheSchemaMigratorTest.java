package org.ce.infrastructure.persistence.migration;

import org.json.JSONObject;
import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ClusterCacheSchemaMigratorTest {

    @Test
    void migrateRootInMemory_infers_missing_cfBasisIndices_and_sets_schema_version() throws Exception {
        JSONObject root = loadBinaryFixture();
        root.getJSONObject("stage3").remove("cfBasisIndices");
        root.remove("schemaVersion");

        boolean migrated = ClusterCacheSchemaMigrator.migrateRootInMemory(root);

        assertTrue(migrated, "Expected migration to run for legacy payload");
        assertEquals(ClusterCacheSchemaMigrator.CURRENT_SCHEMA_VERSION, root.getInt("schemaVersion"));
        assertTrue(root.getJSONObject("stage3").has("cfBasisIndices"));

        int tcf = root.getJSONObject("stage2").getInt("tcf");
        int ncf = root.getJSONObject("stage2").getInt("ncf");
        int nxcf = tcf - ncf;

        var cfBasis = root.getJSONObject("stage3").getJSONArray("cfBasisIndices");
        assertEquals(tcf, cfBasis.length());

        // Binary: all point CF columns should map to sigma^1.
        for (int col = ncf; col < ncf + nxcf; col++) {
            var arr = cfBasis.getJSONArray(col);
            assertEquals(1, arr.length());
            assertEquals(1, arr.getInt(0));
        }
    }

    @Test
    void migrateRootInMemory_noop_for_current_schema_with_cfBasisIndices() throws Exception {
        JSONObject root = loadBinaryFixture();
        root.put("schemaVersion", ClusterCacheSchemaMigrator.CURRENT_SCHEMA_VERSION);
        if (!root.getJSONObject("stage3").has("cfBasisIndices")) {
            ClusterCacheSchemaMigrator.migrateRootInMemory(root);
        }

        boolean migrated = ClusterCacheSchemaMigrator.migrateRootInMemory(root);
        assertFalse(migrated, "Expected no-op migration for current schema payload");
    }

    private static JSONObject loadBinaryFixture() throws Exception {
        Path file = findProjectRoot()
                .resolve("data")
                .resolve("cluster_cache")
                .resolve("BCC_A2_T_bin")
                .resolve("all_cluster_data.json");
        return new JSONObject(Files.readString(file));
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


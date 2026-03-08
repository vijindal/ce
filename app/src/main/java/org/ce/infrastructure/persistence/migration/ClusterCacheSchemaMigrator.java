package org.ce.infrastructure.persistence.migration;

import org.ce.infrastructure.persistence.ClusterDataSerializer;
import org.json.JSONObject;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.stream.Stream;

/**
 * Schema migration utilities for all_cluster_data.json cache payloads.
 */
public final class ClusterCacheSchemaMigrator {

    public static final int CURRENT_SCHEMA_VERSION = 2;
    private static final String FILE_NAME = "all_cluster_data.json";

    private ClusterCacheSchemaMigrator() {
    }

    public static boolean migrateRootInMemory(JSONObject root) {
        if (!root.has("stage3")) {
            return false;
        }

        JSONObject stage3 = root.getJSONObject("stage3");
        boolean needsStage3Basis = !stage3.has("cfBasisIndices");
        boolean needsVersionStamp = root.optInt("schemaVersion", 0) < CURRENT_SCHEMA_VERSION;

        if (!needsStage3Basis && !needsVersionStamp) {
            return false;
        }

        if (needsStage3Basis) {
            JSONObject stage2 = root.has("stage2") ? root.getJSONObject("stage2") : null;
            int numComponents = root.getInt("numComponents");
            int[][] inferred = LegacyStage3CfBasisIndicesInferer.inferFromStage2(stage2, numComponents);
            stage3.put("cfBasisIndices", ClusterDataSerializer.int2DToJson(inferred));
        }

        root.put("schemaVersion", CURRENT_SCHEMA_VERSION);
        return true;
    }

    public static CacheMigrationReport migrateAllUnder(Path clusterCacheRoot) {
        int scanned = 0;
        int migrated = 0;
        int failed = 0;

        if (!Files.exists(clusterCacheRoot)) {
            return new CacheMigrationReport(0, 0, 0);
        }

        try (Stream<Path> stream = Files.walk(clusterCacheRoot)) {
            for (Path file : (Iterable<Path>) stream::iterator) {
                if (!file.getFileName().toString().equals(FILE_NAME)) {
                    continue;
                }
                scanned++;
                try {
                    String raw = Files.readString(file);
                    JSONObject root = new JSONObject(raw);
                    if (migrateRootInMemory(root)) {
                        Files.writeString(file, root.toString(2));
                        migrated++;
                    }
                } catch (Exception ex) {
                    failed++;
                }
            }
        } catch (Exception ex) {
            return new CacheMigrationReport(scanned, migrated, failed + 1);
        }

        return new CacheMigrationReport(scanned, migrated, failed);
    }
}


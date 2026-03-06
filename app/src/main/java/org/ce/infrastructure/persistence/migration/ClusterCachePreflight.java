package org.ce.infrastructure.persistence.migration;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.function.Consumer;

/**
 * One-time preflight migration runner for runtime cache files.
 */
public final class ClusterCachePreflight {

    private static volatile boolean preflightDone = false;

    private ClusterCachePreflight() {
    }

    public static synchronized void runOnce(Consumer<String> logger) {
        if (preflightDone) {
            return;
        }

        Consumer<String> safeLogger = logger != null ? logger : msg -> { };
        Path root = findProjectRoot().resolve("data").resolve("cluster_cache");

        CacheMigrationReport report = ClusterCacheSchemaMigrator.migrateAllUnder(root);
        safeLogger.accept("[CachePreflight] scanned=" + report.filesScanned()
                + " migrated=" + report.filesMigrated()
                + " failed=" + report.filesFailed());

        preflightDone = true;
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


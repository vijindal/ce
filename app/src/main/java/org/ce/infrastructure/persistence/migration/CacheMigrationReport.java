package org.ce.infrastructure.persistence.migration;

/**
 * Summary of cache migration activity.
 */
public record CacheMigrationReport(int filesScanned, int filesMigrated, int filesFailed) {

    public boolean hasFailures() {
        return filesFailed > 0;
    }
}


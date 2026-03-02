package org.ce.workbench.model;

import java.time.LocalDateTime;

/**
 * Thread-safe runtime status for a system.
 *
 * <p>Tracks mutable state: whether clusters/CFs have been computed,
 * whether CEC values are available, and computation timestamps.
 * This class is managed exclusively by
 * {@link org.ce.workbench.backend.registry.SystemRegistry}.</p>
 */
public final class SystemStatus {

    private volatile boolean clustersComputed;
    private volatile boolean cfsComputed;
    private volatile boolean cecAvailable;
    private volatile LocalDateTime clustersComputedDate;
    private volatile LocalDateTime cfsComputedDate;

    public SystemStatus() {
        this.clustersComputed = false;
        this.cfsComputed = false;
        this.cecAvailable = false;
    }

    // -------------------------------------------------------------------------
    // Accessors (thread-safe via volatile)
    // -------------------------------------------------------------------------

    public boolean isClustersComputed() { return clustersComputed; }
    public boolean isCfsComputed() { return cfsComputed; }
    public boolean isCecAvailable() { return cecAvailable; }
    public LocalDateTime getClustersComputedDate() { return clustersComputedDate; }
    public LocalDateTime getCfsComputedDate() { return cfsComputedDate; }

    // -------------------------------------------------------------------------
    // Mutators (synchronized for compound updates)
    // -------------------------------------------------------------------------

    public synchronized void setClustersComputed(boolean computed) {
        this.clustersComputed = computed;
        if (computed) {
            this.clustersComputedDate = LocalDateTime.now();
        }
    }

    public synchronized void setCfsComputed(boolean computed) {
        this.cfsComputed = computed;
        if (computed) {
            this.cfsComputedDate = LocalDateTime.now();
        }
    }

    public synchronized void setCecAvailable(boolean available) {
        this.cecAvailable = available;
    }

    /**
     * Resets all computed flags (e.g., when system config changes).
     */
    public synchronized void resetComputedState() {
        this.clustersComputed = false;
        this.cfsComputed = false;
        this.clustersComputedDate = null;
        this.cfsComputedDate = null;
    }

    /**
     * Bulk update for restoring state from persistence.
     */
    public synchronized void restore(
            boolean clustersComputed,
            boolean cfsComputed,
            boolean cecAvailable,
            LocalDateTime clustersComputedDate,
            LocalDateTime cfsComputedDate) {
        this.clustersComputed = clustersComputed;
        this.cfsComputed = cfsComputed;
        this.cecAvailable = cecAvailable;
        this.clustersComputedDate = clustersComputedDate;
        this.cfsComputedDate = cfsComputedDate;
    }

    @Override
    public String toString() {
        return "SystemStatus{" +
                "clustersComputed=" + clustersComputed +
                ", cfsComputed=" + cfsComputed +
                ", cecAvailable=" + cecAvailable +
                '}';
    }
}

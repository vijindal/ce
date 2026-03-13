package org.ce.application.job;

import org.ce.domain.model.data.AllClusterData;
import org.ce.domain.system.SystemIdentity;

import java.util.Objects;

/**
 * Immutable result record produced by {@link CECAssemblyJob}.
 *
 * <p>Replaces the four mutable fields that were previously scattered across
 * {@code CECManagementPanel} ({@code assemblyCfOrderMap}, {@code assemblyTransformedByOrder},
 * {@code assemblyTargetData}, {@code currentAssemblyTarget}).  A single instance can be
 * safely passed between threads and stored as a snapshot that will never go stale.</p>
 *
 * @param targetSystem   the system for which the assembly was performed
 * @param targetData     the {@link AllClusterData} loaded for {@code targetSystem}
 * @param cfOrderMap     CF order classification array (index → minimum order M)
 * @param derivedECIs    assembled ECI values contributed by all subsystem orders
 * @param pureKCount     number of pure-K CFs that require manual user input
 */
public record AssemblyResult(
        SystemIdentity targetSystem,
        AllClusterData targetData,
        int[] cfOrderMap,
        double[] derivedECIs,
        int pureKCount
) {
    public AssemblyResult {
        Objects.requireNonNull(targetSystem, "targetSystem");
        Objects.requireNonNull(targetData,   "targetData");
        Objects.requireNonNull(cfOrderMap,   "cfOrderMap");
        Objects.requireNonNull(derivedECIs,  "derivedECIs");
        if (pureKCount < 0) throw new IllegalArgumentException("pureKCount must be >= 0");
    }
}

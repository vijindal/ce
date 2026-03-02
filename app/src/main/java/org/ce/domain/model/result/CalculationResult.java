package org.ce.domain.model.result;

import java.time.Instant;

/**
 * Sealed interface representing the outcome of a thermodynamic calculation.
 *
 * <p>This is the root of a sealed hierarchy that enables exhaustive pattern
 * matching over all possible calculation outcomes:</p>
 * <ul>
 *   <li>{@link CVMResult} - Successful CVM free-energy minimization</li>
 *   <li>{@link MCSResult} - Successful Monte Carlo simulation</li>
 *   <li>{@link CalculationFailure} - Failed calculation with error details</li>
 * </ul>
 *
 * <h2>Usage</h2>
 * <pre>{@code
 * CalculationResult result = service.calculateCVM(request);
 * String summary = switch (result) {
 *     case CVMResult cvm -> "G = " + cvm.gibbsEnergy();
 *     case MCSResult mcs -> "⟨E⟩ = " + mcs.energyPerSite();
 *     case CalculationFailure f -> "FAILED: " + f.errorMessage();
 * };
 * }</pre>
 *
 * @since 2.0
 */
public sealed interface CalculationResult 
        permits ThermodynamicResult, CalculationFailure {

    /**
     * Returns the timestamp when this result was produced.
     */
    Instant timestamp();

    /**
     * Returns a human-readable summary of this result.
     */
    String summary();

    /**
     * Returns true if this result represents a successful calculation.
     */
    default boolean isSuccess() {
        return this instanceof ThermodynamicResult;
    }

    /**
     * Returns true if this result represents a failed calculation.
     */
    default boolean isFailure() {
        return this instanceof CalculationFailure;
    }
}

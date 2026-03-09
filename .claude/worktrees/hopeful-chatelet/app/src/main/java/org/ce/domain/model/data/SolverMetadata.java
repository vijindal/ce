package org.ce.domain.model.data;

/**
 * Sealed interface for method-specific solver metadata.
 *
 * <p>Provides type-safe access to solver-specific details without
 * polluting a single result class with fields from multiple methods.</p>
 *
 * @see CVMMetadata
 * @see MCSMetadata
 */
public sealed interface SolverMetadata permits CVMMetadata, MCSMetadata {
    
    /**
     * Returns the calculation method this metadata belongs to.
     */
    CalculationMethod method();
    
    /**
     * Returns the wall-clock execution time in milliseconds.
     */
    long wallClockTimeMs();
}


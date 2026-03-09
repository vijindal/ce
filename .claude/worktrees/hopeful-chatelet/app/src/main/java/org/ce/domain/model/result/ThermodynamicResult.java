package org.ce.domain.model.result;

/**
 * Sealed interface for successful thermodynamic calculation results.
 *
 * <p>Extends {@link CalculationResult} with common thermodynamic quantities
 * shared by both CVM and MCS calculations:</p>
 * <ul>
 *   <li>Temperature</li>
 *   <li>Composition</li>
 *   <li>Average correlation functions</li>
 * </ul>
 *
 * @since 2.0
 */
public sealed interface ThermodynamicResult extends CalculationResult 
        permits CVMResult, MCSResult {

    /**
     * Returns the calculation temperature in Kelvin.
     */
    double temperature();

    /**
     * Returns the composition (mole fraction of component B for binary).
     */
    double composition();

    /**
     * Returns the equilibrium/average correlation functions.
     *
     * <p>For CVM, these are the equilibrium CF values; for MCS, these are
     * the ensemble averages âŸ¨u_tâŸ©.</p>
     */
    double[] correlationFunctions();

    /**
     * Returns the number of cluster types (length of CF array).
     */
    default int numClusterTypes() {
        return correlationFunctions().length;
    }
}


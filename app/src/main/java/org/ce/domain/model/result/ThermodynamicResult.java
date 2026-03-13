package org.ce.domain.model.result;

/**
 * Sealed interface for successful thermodynamic calculation results.
 *
 * <p>Extends {@link CalculationResult} with common thermodynamic quantities
 * shared by all calculation engines (CVM, MCS):</p>
 * <ul>
 *   <li>Temperature and composition</li>
 *   <li>Equilibrium/ensemble-average correlation functions</li>
 *   <li>Mixing enthalpy (computed by both CVM and MCS from the CE Hamiltonian)</li>
 * </ul>
 *
 * <p>The canonical result type is {@link EquilibriumState}. The legacy types
 * {@link CVMResult} and {@link MCSResult} remain for backward compatibility.</p>
 *
 * @since 2.0
 */
public sealed interface ThermodynamicResult extends CalculationResult
        permits CVMResult, MCSResult, EquilibriumState {

    /**
     * Returns the calculation temperature in Kelvin.
     */
    double temperature();

    /**
     * Returns the B-component mole fraction (x[1]) for binary systems.
     *
     * <p>Prefer {@link #compositionArray()} for generality across all K.</p>
     *
     * @deprecated Use {@link #compositionArray()} which works for any number of components
     */
    @Deprecated
    double composition();

    /**
     * Returns the full mole-fraction array x[0..K-1] for all components.
     *
     * <p>This is the canonical composition accessor — use it in all new code.
     * Works uniformly for binary, ternary, and any higher-order system.</p>
     *
     * <p>The default implementation constructs {@code [1-x_B, x_B]} from the
     * deprecated scalar {@link #composition()} for legacy subtypes.
     * {@link EquilibriumState} overrides this with the actual stored array.</p>
     */
    default double[] compositionArray() {
        return new double[]{1.0 - composition(), composition()};
    }

    /**
     * Returns the equilibrium/average correlation functions.
     *
     * <p>For CVM, these are the N-R equilibrium CF values; for MCS, these are
     * the ensemble averages ⟨u_t⟩.</p>
     */
    double[] correlationFunctions();

    /**
     * Returns the mixing enthalpy per mole in J/mol.
     *
     * <p>Both engines compute this from the same CE formula:
     * {@code H = Σ_t ECI[t]·msdis[t]·⟨u_t⟩} (multi-site clusters only).
     * For CVM this equals {@link CVMResult#enthalpy()}; for MCS this equals
     * {@link MCSResult#hmixPerSite()}.</p>
     */
    double enthalpyOfMixing();

    /**
     * Returns the number of cluster types (length of CF array).
     */
    default int numClusterTypes() {
        return correlationFunctions().length;
    }
}
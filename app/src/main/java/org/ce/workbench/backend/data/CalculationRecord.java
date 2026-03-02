package org.ce.workbench.backend.data;

import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.Objects;

/**
 * Immutable record of a completed calculation result.
 *
 * <p>Stores only <em>actually computed</em> values from CVM or MCS calculations,
 * avoiding the "wishlist object" anti-pattern of including fields that are never
 * populated.</p>
 *
 * <h2>Design Rationale</h2>
 * <p>This record replaces the previous {@code CalculationResults} class which mixed
 * CVM-specific fields (correlation functions) with MCS-specific fields (acceptance rate)
 * and included many never-populated wishlist fields (SRO, vibrational entropy, etc.).</p>
 *
 * <p>The clean chain is now:</p>
 * <pre>
 *   AllClusterData (prereqs) → CVMSolverResult | MCResult (raw output) → CalculationRecord (persistence)
 * </pre>
 *
 * <h2>Units</h2>
 * <ul>
 *   <li>Temperature: Kelvin</li>
 *   <li>Gibbs energy: J/mol</li>
 *   <li>Enthalpy: J/mol</li>
 *   <li>Entropy: J/(mol·K)</li>
 *   <li>Composition: mole fraction (0-1)</li>
 * </ul>
 *
 * @param id           unique identifier for this result
 * @param systemId     system this calculation was run for (e.g., "Ti-Nb_BCC_A2_T_bin")
 * @param timestamp    when the calculation completed
 * @param method       calculation method (CVM or MCS)
 * @param temperature  temperature in Kelvin
 * @param composition  mole fractions x[c] for each component
 * @param gibbsEnergy  Gibbs free energy of mixing (J/mol)
 * @param enthalpy     enthalpy of mixing (J/mol)
 * @param entropy      entropy of mixing (J/(mol·K))
 * @param metadata     method-specific solver metadata
 *
 * @see CVMMetadata
 * @see MCSMetadata
 * @see AllClusterData
 */
public record CalculationRecord(
        String id,
        String systemId,
        LocalDateTime timestamp,
        CalculationMethod method,
        double temperature,
        double[] composition,
        double gibbsEnergy,
        double enthalpy,
        double entropy,
        SolverMetadata metadata
) {
    
    /**
     * Canonical constructor with validation.
     */
    public CalculationRecord {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(systemId, "systemId");
        Objects.requireNonNull(timestamp, "timestamp");
        Objects.requireNonNull(method, "method");
        Objects.requireNonNull(composition, "composition");
        Objects.requireNonNull(metadata, "metadata");
        
        if (composition.length == 0) {
            throw new IllegalArgumentException("composition must have at least one component");
        }
        if (metadata.method() != method) {
            throw new IllegalArgumentException("metadata.method() must match method");
        }
        
        // Defensive copy
        composition = composition.clone();
    }
    
    /**
     * Returns a defensive copy of the composition array.
     */
    @Override
    public double[] composition() {
        return composition.clone();
    }
    
    /**
     * Convenience: returns x[1] (B-fraction) for binary systems.
     */
    public double compositionB() {
        return composition.length > 1 ? composition[1] : 0.0;
    }
    
    /**
     * Returns a human-readable summary.
     */
    public String summary() {
        return String.format(
            "%s @ T=%.1f K, x=%.3f: G=%.4f J/mol, H=%.4f J/mol, S=%.6f J/(mol·K)",
            method,
            temperature,
            compositionB(),
            gibbsEnergy,
            enthalpy,
            entropy
        );
    }
    
    // =========================================================================
    // Factory methods
    // =========================================================================
    
    /**
     * Creates a CalculationRecord from a CVM solver result.
     *
     * @param id          unique result identifier
     * @param systemId    system identifier
     * @param temperature temperature in Kelvin
     * @param composition mole fractions
     * @param result      CVM solver result
     * @param wallClockMs execution time in milliseconds
     * @return new CalculationRecord
     */
    public static CalculationRecord fromCVM(
            String id,
            String systemId,
            double temperature,
            double[] composition,
            org.ce.cvm.CVMSolverResult result,
            long wallClockMs) {
        
        return new CalculationRecord(
            id,
            systemId,
            LocalDateTime.now(),
            CalculationMethod.CVM,
            temperature,
            composition,
            result.getGibbsEnergy(),
            result.getEnthalpy(),
            result.getEntropy(),
            CVMMetadata.from(result, wallClockMs)
        );
    }
    
    /**
     * Creates a CalculationRecord from an MC simulation result.
     *
     * <p>Note: MCS returns energy per site. This is converted to enthalpy
     * per mole assuming the site energy is in eV and using standard conversion.</p>
     *
     * @param id          unique result identifier
     * @param systemId    system identifier
     * @param result      MC simulation result
     * @param wallClockMs execution time in milliseconds
     * @return new CalculationRecord
     */
    public static CalculationRecord fromMCS(
            String id,
            String systemId,
            org.ce.mcs.MCResult result,
            long wallClockMs) {
        
        // MCS provides energy per site; convert to J/mol
        // eV/site → J/mol: multiply by N_A * e ≈ 96485.3 (Faraday constant)
        double eVtoJmol = 96485.3;
        double enthalpy = result.getEnergyPerSite() * eVtoJmol;
        
        // Gibbs energy and entropy not directly available from MCS
        // G = H - T*S, but we don't have S from MCS directly
        // Store enthalpy and mark G/S as NaN to indicate not computed
        return new CalculationRecord(
            id,
            systemId,
            LocalDateTime.now(),
            CalculationMethod.MCS,
            result.getTemperature(),
            result.getComposition(),
            Double.NaN,  // G not directly computed by MCS
            enthalpy,
            Double.NaN,  // S not directly computed by MCS
            MCSMetadata.from(result, wallClockMs)
        );
    }
    
    @Override
    public String toString() {
        return String.format("CalculationRecord[%s, %s, T=%.1fK]", id, method, temperature);
    }
    
    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof CalculationRecord that)) return false;
        return id.equals(that.id);
    }
    
    @Override
    public int hashCode() {
        return id.hashCode();
    }
}

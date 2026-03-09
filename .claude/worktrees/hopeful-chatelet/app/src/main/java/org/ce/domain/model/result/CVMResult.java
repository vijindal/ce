package org.ce.domain.model.result;

import java.time.Instant;
import java.util.Objects;

/**
 * Immutable result of a CVM (Cluster Variation Method) free-energy minimization.
 *
 * <p>Contains the equilibrium correlation functions, thermodynamic quantities
 * (G, H, S), and convergence metadata.</p>
 *
 * @param temperature     calculation temperature in Kelvin
 * @param composition     composition (mole fraction of B)
 * @param equilibriumCFs  equilibrium correlation function values
 * @param gibbsEnergy     Gibbs energy of mixing (J/mol)
 * @param enthalpy        enthalpy of mixing (J/mol)
 * @param entropy         entropy of mixing (J/(molÂ·K))
 * @param iterations      number of Newton-Raphson iterations
 * @param gradientNorm    â€–âˆ‡Gâ€– at convergence
 * @param converged       whether solver converged within tolerance
 * @param timestamp       when this result was computed
 *
 * @since 2.0
 */
public record CVMResult(
        double temperature,
        double composition,
        double[] equilibriumCFs,
        double gibbsEnergy,
        double enthalpy,
        double entropy,
        int iterations,
        double gradientNorm,
        boolean converged,
        Instant timestamp
) implements ThermodynamicResult {

    /**
     * Canonical constructor with validation.
     */
    public CVMResult {
        Objects.requireNonNull(equilibriumCFs, "equilibriumCFs");
        Objects.requireNonNull(timestamp, "timestamp");
        equilibriumCFs = equilibriumCFs.clone(); // defensive copy
    }

    /**
     * Convenience factory for creating from solver output (uses current time).
     */
    public static CVMResult fromSolver(
            double temperature, double composition,
            double[] equilibriumCFs, double gibbsEnergy,
            double enthalpy, double entropy,
            int iterations, double gradientNorm, boolean converged) {
        return new CVMResult(
                temperature, composition, equilibriumCFs.clone(),
                gibbsEnergy, enthalpy, entropy,
                iterations, gradientNorm, converged,
                Instant.now()
        );
    }

    @Override
    public double[] correlationFunctions() {
        return equilibriumCFs.clone();
    }

    @Override
    public String summary() {
        return String.format("""
                CVM Result [%s]
                â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•
                  Temperature:   %.1f K
                  Composition:   %.4f
                  Converged:     %s (%d iterations)
                  Gibbs Energy:  %.6e J/mol
                  Enthalpy:      %.6e J/mol
                  Entropy:       %.6e J/(molÂ·K)
                  â€–âˆ‡Gâ€–:          %.6e
                â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•""",
                converged ? "CONVERGED" : "NOT CONVERGED",
                temperature, composition, converged, iterations,
                gibbsEnergy, enthalpy, entropy, gradientNorm);
    }
}


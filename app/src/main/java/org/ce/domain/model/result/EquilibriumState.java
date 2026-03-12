package org.ce.domain.model.result;

import java.time.Instant;
import java.util.Objects;
import java.util.OptionalDouble;

/**
 * Unified immutable result of any thermodynamic equilibrium calculation.
 *
 * <p>Both CVM (Cluster Variation Method) and MCS (Monte Carlo Simulation) engines
 * share the same input model (AllClusterData + ECI + T + x) and produce the same
 * class of outputs. This record is the canonical shared result type.</p>
 *
 * <h2>Physics boundary</h2>
 * <p>MCS (canonical ensemble) cannot compute Gibbs energy or entropy from a single
 * run — those require thermodynamic integration over temperature. Accordingly,
 * {@code gibbsEnergy} and {@code entropy} are {@link OptionalDouble#empty()} on the
 * MCS path. {@code heatCapacity} (from the fluctuation formula) is empty for CVM
 * since it is not computed per single run. Use {@link #metrics()} to distinguish
 * which engine produced this state.</p>
 *
 * <h2>Usage</h2>
 * <pre>{@code
 * EquilibriumState state = engine.minimize(T, x);
 * double H = state.enthalpy();
 * state.gibbsEnergy().ifPresent(G -> System.out.printf("G = %.4f%n", G));
 * switch (state.metrics()) {
 *     case EngineMetrics.CvmMetrics m -> System.out.println("NR: " + m.iterations() + " iters");
 *     case EngineMetrics.McsMetrics m -> System.out.printf("MC accept: %.1f%%%n", m.acceptRate()*100);
 * }
 * }</pre>
 *
 * @param temperature        equilibrium temperature in Kelvin
 * @param compositionArray   composition fractions x[c] for each component c
 * @param correlationFunctions  equilibrium/ensemble-average CFs ⟨u_t⟩ (multi-site only)
 * @param enthalpy           mixing enthalpy per mole (J/mol) — available from both engines
 * @param gibbsEnergy        Gibbs energy of mixing (J/mol) — CVM only; empty for MCS
 * @param entropy            mixing entropy per mole (J/(mol·K)) — CVM only; empty for MCS
 * @param heatCapacity       heat capacity per site (J/(mol·K)) — MCS only; empty for CVM
 * @param metrics            engine-specific diagnostics
 * @param timestamp          when this equilibrium state was computed
 *
 * @since 2.1
 */
public record EquilibriumState(
        double temperature,
        double[] compositionArray,
        double[] correlationFunctions,
        double enthalpy,
        OptionalDouble gibbsEnergy,
        OptionalDouble entropy,
        OptionalDouble heatCapacity,
        EngineMetrics metrics,
        Instant timestamp
) implements ThermodynamicResult {

    /**
     * Canonical constructor — validates and defensively copies arrays.
     */
    public EquilibriumState {
        Objects.requireNonNull(compositionArray, "compositionArray");
        Objects.requireNonNull(correlationFunctions, "correlationFunctions");
        Objects.requireNonNull(gibbsEnergy, "gibbsEnergy");
        Objects.requireNonNull(entropy, "entropy");
        Objects.requireNonNull(heatCapacity, "heatCapacity");
        Objects.requireNonNull(metrics, "metrics");
        Objects.requireNonNull(timestamp, "timestamp");
        if (compositionArray.length < 2) {
            throw new IllegalArgumentException("compositionArray must have >= 2 entries");
        }
        compositionArray = compositionArray.clone();
        correlationFunctions = correlationFunctions.clone();
    }

    // -------------------------------------------------------------------------
    // ThermodynamicResult interface implementation
    // -------------------------------------------------------------------------

    /** Returns the B-component mole fraction (x[1]) for binary systems. */
    @Override
    public double composition() {
        return compositionArray[1];
    }

    /** Returns a defensive copy of the correlation function array. */
    @Override
    public double[] correlationFunctions() {
        return correlationFunctions.clone();
    }

    /** Returns a defensive copy of the composition array. */
    @Override
    public double[] compositionArray() {
        return compositionArray.clone();
    }

    /** Mixing enthalpy per mole — same CE formula for both CVM and MCS. */
    @Override
    public double enthalpyOfMixing() {
        return enthalpy;
    }

    @Override
    public Instant timestamp() {
        return timestamp;
    }

    @Override
    public String summary() {
        StringBuilder sb = new StringBuilder();
        sb.append(String.format("""
                EquilibriumState [%s]
                ═══════════════════════════════════════════
                  Temperature:   %.1f K
                  Composition:   %.4f (x_B)
                  Enthalpy:      %.6e J/mol
                """,
                metrics.getClass().getSimpleName().replace("Metrics", ""),
                temperature, composition(), enthalpy));
        gibbsEnergy.ifPresent(G -> sb.append(String.format("  Gibbs Energy:  %.6e J/mol%n", G)));
        entropy.ifPresent(S -> sb.append(String.format("  Entropy:       %.6e J/(mol·K)%n", S)));
        heatCapacity.ifPresent(Cv -> sb.append(String.format("  Cv/site:       %.6e J/(mol·K)%n", Cv)));
        switch (metrics) {
            case EngineMetrics.CvmMetrics m -> sb.append(String.format(
                    "  NR: %s (%d iters, ‖∇G‖=%.2e)%n",
                    m.converged() ? "CONVERGED" : "NOT CONVERGED", m.iterations(), m.gradientNorm()));
            case EngineMetrics.McsMetrics m -> sb.append(String.format(
                    "  MC: accept=%.1f%%, equil=%d, avg=%d, L=%d (%d sites)%n",
                    m.acceptRate() * 100, m.nEquilSweeps(), m.nAvgSweeps(),
                    m.supercellSize(), m.nSites()));
        }
        sb.append("═══════════════════════════════════════════");
        return sb.toString();
    }

    // -------------------------------------------------------------------------
    // Convenience factories
    // -------------------------------------------------------------------------

    /**
     * Factory for CVM results.
     */
    public static EquilibriumState fromCvm(
            double temperature, double[] compositionArray, double[] correlationFunctions,
            double enthalpy, double gibbsEnergy, double entropy,
            boolean converged, int iterations, double gradientNorm) {
        return new EquilibriumState(
                temperature, compositionArray, correlationFunctions,
                enthalpy,
                OptionalDouble.of(gibbsEnergy),
                OptionalDouble.of(entropy),
                OptionalDouble.empty(),
                new EngineMetrics.CvmMetrics(converged, iterations, gradientNorm),
                Instant.now());
    }

    /**
     * Factory for MCS results.
     */
    public static EquilibriumState fromMcs(
            double temperature, double[] compositionArray, double[] correlationFunctions,
            double enthalpy, double heatCapacity,
            double acceptRate, long nEquilSweeps, long nAvgSweeps,
            int supercellSize, int nSites) {
        return new EquilibriumState(
                temperature, compositionArray, correlationFunctions,
                enthalpy,
                OptionalDouble.empty(),
                OptionalDouble.empty(),
                OptionalDouble.of(heatCapacity),
                new EngineMetrics.McsMetrics(acceptRate, nEquilSweeps, nAvgSweeps, supercellSize, nSites),
                Instant.now());
    }
}

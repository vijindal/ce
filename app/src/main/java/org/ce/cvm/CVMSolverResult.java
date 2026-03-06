package org.ce.cvm;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Immutable result of a CVM Newton-Raphson free-energy minimisation.
 *
 * <p>Contains the equilibrium correlation functions, thermodynamic quantities,
 * and convergence metadata.</p>
 */
public final class CVMSolverResult {

    /** Per-iteration Newton-Raphson diagnostics. */
    public static final class IterationSnapshot {
        private final int iteration;
        private final double gibbsEnergy;
        private final double enthalpy;
        private final double entropy;
        private final double gradientNorm;
        private final double[] cf;
        private final double[] dGdu;

        public IterationSnapshot(
                int iteration,
                double gibbsEnergy,
                double enthalpy,
                double entropy,
                double gradientNorm,
                double[] cf,
                double[] dGdu) {
            this.iteration = iteration;
            this.gibbsEnergy = gibbsEnergy;
            this.enthalpy = enthalpy;
            this.entropy = entropy;
            this.gradientNorm = gradientNorm;
            this.cf = cf;
            this.dGdu = dGdu;
        }

        public int getIteration() { return iteration; }
        public double getGibbsEnergy() { return gibbsEnergy; }
        public double getEnthalpy() { return enthalpy; }
        public double getEntropy() { return entropy; }
        public double getGradientNorm() { return gradientNorm; }
        public double[] getCf() { return cf; }
        public double[] getDGdu() { return dGdu; }
    }

    private final double[] equilibriumCFs;  // u[0..ncf-1] at equilibrium
    private final double   gibbsEnergy;     // G = H − T·S at equilibrium
    private final double   enthalpy;        // H at equilibrium
    private final double   entropy;         // S at equilibrium
    private final int      iterations;      // Number of NR iterations performed
    private final double   gradientNorm;    // ||Gcu|| at convergence
    private final boolean  converged;       // Whether tolerance was reached
    private final List<IterationSnapshot> iterationTrace;

    public CVMSolverResult(
            double[] equilibriumCFs,
            double gibbsEnergy,
            double enthalpy,
            double entropy,
            int iterations,
            double gradientNorm,
            boolean converged) {
        this(equilibriumCFs, gibbsEnergy, enthalpy, entropy, iterations, gradientNorm, converged,
                Collections.emptyList());
    }

    public CVMSolverResult(
            double[] equilibriumCFs,
            double gibbsEnergy,
            double enthalpy,
            double entropy,
            int iterations,
            double gradientNorm,
            boolean converged,
            List<IterationSnapshot> iterationTrace) {
        this.equilibriumCFs = equilibriumCFs;
        this.gibbsEnergy = gibbsEnergy;
        this.enthalpy = enthalpy;
        this.entropy = entropy;
        this.iterations = iterations;
        this.gradientNorm = gradientNorm;
        this.converged = converged;
        this.iterationTrace = new ArrayList<>(iterationTrace);
    }

    // =========================================================================
    // Accessors
    // =========================================================================

    /** Equilibrium non-point CF values (length ncf). */
    public double[] getEquilibriumCFs() { return equilibriumCFs; }

    /** Gibbs energy of mixing (J/mol). */
    public double getGibbsEnergy() { return gibbsEnergy; }

    /** Enthalpy of mixing (J/mol). */
    public double getEnthalpy() { return enthalpy; }

    /** Entropy of mixing (J/(mol·K)). */
    public double getEntropy() { return entropy; }

    /** Number of Newton-Raphson iterations performed. */
    public int getIterations() { return iterations; }

    /** Norm of the gradient at the final iterate (convergence check value). */
    public double getGradientNorm() { return gradientNorm; }

    /** Whether the solver converged within tolerance. */
    public boolean isConverged() { return converged; }

    /** Per-iteration Newton-Raphson trace (CFs and dG/du). */
    public List<IterationSnapshot> getIterationTrace() { return new ArrayList<>(iterationTrace); }

    // =========================================================================
    // Display
    // =========================================================================

    /**
     * Returns a multi-line summary suitable for the results log.
     */
    public String toSummary() {
        StringBuilder sb = new StringBuilder();
        sb.append("╔══════════════════════════════════════════════════╗\n");
        sb.append("║           CVM SOLVER RESULT                      ║\n");
        sb.append("╚══════════════════════════════════════════════════╝\n");
        sb.append("  Converged:      ").append(converged).append("\n");
        sb.append("  Iterations:     ").append(iterations).append("\n");
        sb.append("  ||∇G||:         ").append(String.format("%.6e", gradientNorm)).append("\n");
        sb.append("  Gibbs energy G: ").append(String.format("%.8f", gibbsEnergy)).append(" J/mol\n");
        sb.append("  Enthalpy H:     ").append(String.format("%.8f", enthalpy)).append(" J/mol\n");
        sb.append("  Entropy S:      ").append(String.format("%.8f", entropy)).append(" J/(mol·K)\n");
        sb.append("  Equilibrium CFs:\n");
        for (int i = 0; i < equilibriumCFs.length; i++) {
            sb.append("    u[").append(i).append("] = ").append(String.format("%.12f", equilibriumCFs[i])).append("\n");
        }
        return sb.toString();
    }
}

package org.ce.infrastructure.cvm;

import org.ce.domain.cvm.CVMPhaseModel;
import org.ce.domain.cvm.CVMModelInput;

/**
 * Example usage patterns for CVMPhaseModel.
 *
 * <p>Demonstrates how to use the new model-centric API for:
 * <ul>
 *   <li>Single point calculations</li>
 *   <li>Temperature scanning</li>
 *   <li>Composition scanning</li>
 *   <li>Phase diagram analysis</li>
 *   <li>Stability evaluation</li>
 *   <li>Multi-component systems</li>
 * </ul>
 */
public class CVMPhaseModelExamples {

    /**
     * Example 1: Single point calculation at fixed (T, x, ECI).
     */
    public static void example_SinglePoint() throws Exception {
        // Assume model input and ECI are prepared
        CVMModelInput input = null; // Prepared elsewhere
        double[] eci = null;                  // Loaded from database

        // Create model with initial parameters
        CVMPhaseModel model = CVMPhaseModel.create(
            input,
            eci,
            1000.0,   // Temperature: 1000 K
            0.5       // Composition: x_B = 0.5
        );

        // Query equilibrium properties (first call triggers minimization)
        System.out.println("=== Equilibrium Properties at T=1000K, x=0.5 ===");
        System.out.println("G = " + model.getEquilibriumG() + " J/mol");
        System.out.println("H = " + model.getEquilibriumH() + " J/mol");
        System.out.println("S = " + model.getEquilibriumS() + " J/(molÂ·K)");

        // Query microstate (uses cached results)
        System.out.println("\nCluster Variables (CVs):");
        double[][][] cvs = model.getEquilibriumCVs();
        System.out.println("  CV shape: [" + cvs.length + "]["
            + cvs[0].length + "]["
            + cvs[0][0].length + "]");

        // Query short-range order
        System.out.println("\nShort-Range Order Parameters (SROs):");
        double[] sro = model.getSROs();
        for (int i = 0; i < sro.length; i++) {
            System.out.println("  SRO[" + i + "] = " + sro[i]);
        }

        // Stability check
        System.out.println("\nStability: " + (model.isStable() ? "STABLE" : "UNSTABLE"));

        System.out.println(model.getSummary());
    }

    /**
     * Example 2: Scan temperature at fixed composition and ECI.
     * Efficient: single model instance, changing parameters.
     */
    public static void example_TemperatureScan() throws Exception {
        CVMModelInput input = null;
        double[] eci = null;

        // Create model at initial condition
        CVMPhaseModel model = CVMPhaseModel.create(input, eci, 300.0, 0.5);

        System.out.println("=== Temperature Scan: x = 0.5 ===");
        System.out.println("T(K)      G(J/mol)       H(J/mol)       S(J/(molÂ·K))");
        System.out.println("-".repeat(70));

        // Scan from 300 to 1500 K
        for (double T = 300; T <= 1500; T += 100) {
            // Change parameter (invalidates cache)
            model.setTemperature(T);

            // Query properties (auto-minimizes if needed, uses cache if not)
            double G = model.getEquilibriumG();
            double H = model.getEquilibriumH();
            double S = model.getEquilibriumS();

            System.out.printf("%-8.0f  %12.3e  %12.3e  %12.6f%n", T, G, H, S);
        }
    }

    /**
     * Example 3: Scan composition at fixed temperature.
     * Useful for understanding composition dependence.
     */
    public static void example_CompositionScan() throws Exception {
        CVMModelInput input = null;
        double[] eci = null;

        // Create model at T = 800 K
        CVMPhaseModel model = CVMPhaseModel.create(input, eci, 800.0, 0.0);

        System.out.println("=== Composition Scan: T = 800 K ===");
        System.out.println("x      G(J/mol)       Stable   ||âˆ‡G||");
        System.out.println("-".repeat(50));

        for (double x = 0.0; x <= 1.0; x += 0.1) {
            model.setComposition(x);

            double G = model.getEquilibriumG();
            boolean stable = model.isStable();
            double gradNorm = model.getGradientNorm();

            String stableStr = stable ? "YES" : "NO";
            System.out.printf("%.1f   %12.3e   %5s   %.3e%n", x, G, stableStr, gradNorm);
        }
    }

    /**
     * Example 4: Phase diagram in T-x space.
     * Identifies stable vs unstable regions.
     */
    public static void example_PhaseDiagram() throws Exception {
        CVMModelInput input = null;
        double[] eci = null;

        // Temperature range
        double[] temperatures = {400, 500, 600, 700, 800, 900, 1000};

        // Composition range
        double[] compositions = new double[11];
        for (int i = 0; i <= 10; i++) {
            compositions[i] = i / 10.0;
        }

        // Create single model instance
        CVMPhaseModel model = CVMPhaseModel.create(input, eci, temperatures[0], compositions[0]);

        System.out.println("=== PHASE DIAGRAM (T-x space) ===");
        System.out.println("'*' = unstable, ' ' = stable");
        System.out.println();

        for (double T : temperatures) {
            model.setTemperature(T);

            System.out.print("T=" + String.format("%4.0f", T) + " K: |");

            for (double x : compositions) {
                model.setComposition(x);

                // Simple stability indicator
                char marker = model.isStable() ? ' ' : '*';
                System.out.print(marker);
            }

            System.out.println("|");
        }

        System.out.println("           " + "+".repeat(11));
        System.out.print("           ");
        for (int i = 0; i <= 10; i++) {
            System.out.print(String.format("%.1f", i / 10.0).charAt(0));
        }
        System.out.println(" (composition)");
    }

    /**
     * Example 5: Multi-component system (K >= 3).
     * Use setMoleFractions for full control.
     */
    public static void example_MultiComponent() throws Exception {
        CVMModelInput input = null; // Must be K=3 system
        double[] eci = null;

        // Create model for ternary system
        // Initial composition: x_A=0.5, x_B=0.3, x_C=0.2
        CVMPhaseModel model = CVMPhaseModel.create(input, eci, 800.0, 0.333);

        System.out.println("=== Ternary System: A-B-C ===");

        // Set specific ternary composition
        double[] fractions = {0.5, 0.3, 0.2};  // x_A, x_B, x_C (sum = 1.0)
        model.setMoleFractions(fractions);

        CVMPhaseModel.EquilibriumState state = model.getEquilibriumState();
        System.out.println("Composition: " + state);
        System.out.println("G = " + state.G + " J/mol");
        System.out.println("Convergence: " + String.format("%.2e", state.convergenceMeasure));
    }

    /**
     * Example 6: Sensitivity to CEC changes.
     * See how changing interaction strengths affects equilibrium.
     */
    public static void example_CECInfluence() throws Exception {
        CVMModelInput input = null;
        double[] eciBase = null;  // Base case

        CVMPhaseModel model = CVMPhaseModel.create(input, eciBase, 800.0, 0.5);

        System.out.println("=== Effect of CEC variation ===");
        System.out.println("Base case G = " + model.getEquilibriumG());

        // Vary first CEC by Â±10%
        double[] eciVaried = eciBase.clone();
        for (double perturbation : new double[]{-0.1, -0.05, 0, 0.05, 0.1}) {
            eciVaried[0] = eciBase[0] * (1.0 + perturbation);
            model.setECI(eciVaried);

            double G = model.getEquilibriumG();
            System.out.printf("ECI[0] Ã— %.2f: G = %.3e%n", 1.0 + perturbation, G);
        }
    }

    /**
     * Example 7: Complete thermodynamic state export.
     * Bundle all properties for external analysis or storage.
     */
    public static void example_FullStateExport() throws Exception {
        CVMModelInput input = null;
        double[] eci = null;

        CVMPhaseModel model = CVMPhaseModel.create(input, eci, 600.0, 0.5);

        // Get complete equilibrium state
        CVMPhaseModel.EquilibriumState state = model.getEquilibriumState();

        System.out.println("=== Complete Equilibrium State ===");
        System.out.println("Temperature: " + state.temperature + " K");
        System.out.println("Composition: " + java.util.Arrays.toString(state.moleFractions));
        System.out.println("\nThermodynamics:");
        System.out.println("  G = " + String.format("%.6e", state.G) + " J/mol");
        System.out.println("  H = " + String.format("%.6e", state.H) + " J/mol");
        System.out.println("  S = " + String.format("%.6f", state.S) + " J/(molÂ·K)");
        System.out.println("\nConvergence:");
        System.out.println("  Iterations: " + state.iterations);
        System.out.println("  ||âˆ‡G||: " + String.format("%.2e", state.convergenceMeasure));
        System.out.println("  Time (ms): " + state.getComputationTimeMs());
        System.out.println("\nCorrelation Functions (first 5): ");
        for (int i = 0; i < Math.min(5, state.correlationFunctions.length); i++) {
            System.out.println("  u[" + i + "] = " + state.correlationFunctions[i]);
        }
    }

    /**
     * Example 8: Efficient batch processing.
     * Scan large parameter grid with single model.
     */
    public static void example_BatchProcessing() throws Exception {
        CVMModelInput input = null;
        double[] eci = null;

        // Create model at first condition
        CVMPhaseModel model = CVMPhaseModel.create(input, eci, 300.0, 0.0);

        double[] temperatures = {400, 600, 800, 1000};
        double[] compositions = new double[21];  // 0.00 to 1.00 step 0.05
        for (int i = 0; i < compositions.length; i++) {
            compositions[i] = i * 0.05;
        }

        System.out.println("=== Batch Processing: " + temperatures.length
            + " temps Ã— " + compositions.length + " compositions ===");

        long startTime = System.currentTimeMillis();
        int count = 0;

        for (double T : temperatures) {
            model.setTemperature(T);

            for (double x : compositions) {
                model.setComposition(x);

                // Minimal query
                double G = model.getEquilibriumG();

                count++;
            }
        }

        long duration = System.currentTimeMillis() - startTime;
        System.out.println("Processed " + count + " points in " + duration + " ms");
        System.out.println("Average: " + (duration / (double) count) + " ms/point");
    }

    /**
     * Example 9: Adaptive sampling.
     * Refine composition grid where things are interesting.
     */
    public static void example_AdaptiveSampling() throws Exception {
        CVMModelInput input = null;
        double[] eci = null;

        CVMPhaseModel model = CVMPhaseModel.create(input, eci, 700.0, 0.5);

        // Coarse grid: 0.0 to 1.0 by 0.1
        System.out.println("=== Coarse Grid ===");
        double[] coarseX = new double[11];
        for (int i = 0; i <= 10; i++) {
            coarseX[i] = i / 10.0;
        }

        double[] coarseG = new double[coarseX.length];
        for (int i = 0; i < coarseX.length; i++) {
            model.setComposition(coarseX[i]);
            coarseG[i] = model.getEquilibriumG();
            System.out.printf("x=%.1f  G=%.3e%n", coarseX[i], coarseG[i]);
        }

        // Find region with maximum curvature (interesting behavior)
        double maxCurvature = 0;
        int maxIdx = 0;
        for (int i = 1; i < coarseX.length - 1; i++) {
            double curvature = Math.abs(
                (coarseG[i + 1] - coarseG[i]) - (coarseG[i] - coarseG[i - 1])
            );
            if (curvature > maxCurvature) {
                maxCurvature = curvature;
                maxIdx = i;
            }
        }

        // Refine that region
        System.out.println("\n=== Fine Grid (refined region) ===");
        double xStart = coarseX[maxIdx - 1];
        double xEnd = coarseX[maxIdx + 1];

        for (double x = xStart; x <= xEnd; x += 0.02) {
            model.setComposition(x);
            double G = model.getEquilibriumG();
            System.out.printf("x=%.2f  G=%.3e%n", x, G);
        }
    }

    // Note: These examples assume a prepared CVMModelInput and ECI array
    // In practice, these would be loaded via CalculationService.prepareCVMModel()
    // and CalculationService.loadECI() methods
}


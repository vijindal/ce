package org.ce.infrastructure.cvm;

import org.ce.domain.cvm.CVMSolverResult;
import org.ce.domain.cvm.CVMPhaseModel;
import org.ce.domain.model.result.EngineMetrics;
import org.ce.domain.model.result.EquilibriumState;
import org.ce.application.port.CalculationProgressListener;
import org.ce.infrastructure.logging.LoggingConfig;

import java.util.List;
import java.util.logging.Logger;

/**
 * Executor for CVM Phase Model calculations.
 *
 * <p>Bridges between CVMPhaseModel and CalculationProgressListener.
 * Provides parameter scanning and query execution with progress updates.</p>
 *
 * <p>CVMPhaseModelExecutor manages a model that can be queried multiple times
 * with different parameters.</p>
 */
public class CVMPhaseModelExecutor {

    private static final Logger LOG = LoggingConfig.getLogger(CVMPhaseModelExecutor.class);

    /**
     * Executes initial thermodynamic evaluation of the CVM phase model.
     *
     * <p>Logs model information and performs first minimization if not already done.</p>
     *
     * @param model    The CVM phase model (already initialized by create)
     * @param listener Progress listener for logging and updates
     * @return true if execution succeeds, false otherwise
     */
    public static boolean initializeModel(CVMPhaseModel model, CalculationProgressListener listener) {
        if (model == null) {
            listener.logMessage("\u2717 CVMPhaseModelExecutor: Model is null");
            return false;
        }

        LOG.fine("CVMPhaseModelExecutor.initializeModel — ENTER: T=" + model.getTemperature() + " K");

        listener.logMessage("\n" + "=".repeat(60));
        listener.logMessage("CVM Phase Model - Initialization Complete");
        listener.logMessage("=".repeat(60));

        try {
            // Model is already minimized in CVMPhaseModel.create()
            // Just log and return success

            EquilibriumState state = model.getEquilibriumState();

            // Extract CVM-specific diagnostics via pattern match on metrics
            EngineMetrics.CvmMetrics cvm = (EngineMetrics.CvmMetrics) state.metrics();

            listener.logMessage("\nInitial Thermodynamic State:");
            listener.logMessage("  G (Gibbs Energy):    "
                    + String.format("%.8e", state.gibbsEnergy().getAsDouble()));
            listener.logMessage("  H (Enthalpy):        "
                    + String.format("%.8e", state.enthalpy()));
            listener.logMessage("  S (Entropy):         "
                    + String.format("%.8e", state.entropy().getAsDouble()));
            listener.logMessage("  Iterations:          " + cvm.iterations());
            listener.logMessage("  Convergence Measure: "
                    + String.format("%.6e", cvm.gradientNorm()));

            listener.logMessage("  Equilibrium CFs (non-point):");
            double[] cfs = state.correlationFunctions();
            for (int i = 0; i < cfs.length; i++) {
                listener.logMessage("    CF[" + i + "]: " + String.format("%.10f", cfs[i]));
            }

            List<CVMSolverResult.IterationSnapshot> trace = model.getLastIterationTrace();
            listener.logMessage("\nN-R Iteration Trace (CFs and dG/du):");
            if (trace.isEmpty()) {
                listener.logMessage("  (no iteration trace available)");
            } else {
                for (CVMSolverResult.IterationSnapshot snap : trace) {
                    listener.logMessage("  Iter " + snap.getIteration()
                            + " | G=" + String.format("%.8e", snap.getGibbsEnergy())
                            + " | ||dG/du||=" + String.format("%.6e", snap.getGradientNorm()));

                    double[] cf = snap.getCf();
                    listener.logMessage("    CF:");
                    for (int i = 0; i < cf.length; i++) {
                        listener.logMessage("      CF[" + i + "]=" + String.format("%.10f", cf[i]));
                    }

                    double[] grad = snap.getDGdu();
                    listener.logMessage("    dG/du:");
                    for (int i = 0; i < grad.length; i++) {
                        listener.logMessage("      dG/du[" + i + "]=" + String.format("%.10e", grad[i]));
                    }
                }
            }

            listener.logMessage("\n\u2714 CVM Phase Model ready for parameter scanning");
            listener.setProgress(1.0);
            listener.logMessage("=".repeat(60));
            LOG.fine("CVMPhaseModelExecutor.initializeModel — EXIT: T=" + model.getTemperature()
                    + " K, G=" + String.format("%.8e", state.gibbsEnergy().getAsDouble())
                    + ", iterations=" + cvm.iterations()
                    + ", convergence=" + String.format("%.2e", cvm.gradientNorm()));
            return true;

        } catch (Exception ex) {
            LOG.warning("CVMPhaseModelExecutor.initializeModel — FAILED: "
                    + ex.getClass().getSimpleName() + ": " + ex.getMessage());
            listener.logMessage("\n\u2717 CVM Phase Model Initialization Failed");
            listener.logMessage("Error: " + ex.getClass().getSimpleName());
            listener.logMessage("Message: " + ex.getMessage());
            listener.logMessage("=".repeat(60));
            return false;
        }
    }

    /**
     * Executes a parameter query on the CVM phase model.
     *
     * <p>Typically used for parameter scanning (T-scan, x-scan, etc).
     * Automatically triggers re-minimization if parameters have changed.</p>
     *
     * @param model         The CVM phase model
     * @param parameterName Description of the query (e.g., "T=1000K")
     * @param listener      Progress listener for logging
     * @return unified EquilibriumState with G, H, S, CFs and CvmMetrics diagnostics
     */
    public static EquilibriumState queryModel(
            CVMPhaseModel model,
            String parameterName,
            CalculationProgressListener listener) {

        try {
            EquilibriumState state = model.getEquilibriumState();

            // Log brief summary (avoid spam during scanning)
            boolean isFullLogging = parameterName.contains("Full") || parameterName.contains("Complete");
            if (isFullLogging && listener != null) {
                EngineMetrics.CvmMetrics cvm = (EngineMetrics.CvmMetrics) state.metrics();
                listener.logMessage(parameterName
                        + ": G=" + String.format("%.8e", state.gibbsEnergy().getAsDouble())
                        + " convergence=" + String.format("%.2e", cvm.gradientNorm()));
            }

            return state;

        } catch (Exception ex) {
            if (listener != null) {
                listener.logMessage("\u2717 Query failed at " + parameterName + ": " + ex.getMessage());
            }
            return null;
        }
    }
}
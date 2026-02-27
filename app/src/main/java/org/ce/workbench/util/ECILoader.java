package org.ce.workbench.util;

import javafx.geometry.Insets;
import javafx.scene.control.*;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.Priority;
import org.ce.workbench.backend.data.SystemDataLoader;

import java.util.Optional;

/**
 * Utility for loading and validating ECI (Effective Cluster Interactions) data.
 *
 * <p>ECI can come from:</p>
 * <ol>
 *   <li>CEC database — element-keyed JSON files under
 *       {@code /data/systems/{elements}/cec.json}</li>
 *   <li>User-provided manual input via a dialog</li>
 * </ol>
 *
 * <h2>Length contract</h2>
 * <p>The ECI array length <strong>must</strong> equal {@code tc} from the
 * cluster identification result.  Any mismatch is surfaced as a clear,
 * user-facing error rather than a silent empty-Optional return.</p>
 */
public class ECILoader {

    // -------------------------------------------------------------------------
    // Public load result type (Bug 7 fix)
    // -------------------------------------------------------------------------

    /**
     * Result of a database CEC lookup.  Carries either the ECI values or a
     * human-readable failure reason so callers can distinguish "not found" from
     * "found but wrong length".
     */
    public static class DBLoadResult {
        public enum Status { OK, NOT_FOUND, LENGTH_MISMATCH }

        public final Status  status;
        public final double[] eci;
        public final int      foundLen;
        public final int      neededLen;
        public final String   message;
        public final boolean  temperatureEvaluated;

        private DBLoadResult(Status status, double[] eci,
                             int foundLen, int neededLen, String message,
                             boolean temperatureEvaluated) {
            this.status               = status;
            this.eci                  = eci;
            this.foundLen             = foundLen;
            this.neededLen            = neededLen;
            this.message              = message;
            this.temperatureEvaluated = temperatureEvaluated;
        }

        static DBLoadResult ok(double[] eci, int len) {
            return new DBLoadResult(Status.OK, eci, len, len,
                    "CEC loaded from database (" + len + " values)", false);
        }
        static DBLoadResult okTempEvaluated(double[] eci, int len, double T) {
            return new DBLoadResult(Status.OK, eci, len, len,
                    "CEC loaded (" + len + " values, evaluated at T=" + T + "K)", true);
        }
        static DBLoadResult notFound(String key, int needed) {
            return new DBLoadResult(Status.NOT_FOUND, null, 0, needed,
                    "No CEC data found for '" + key + "'.", false);
        }
        static DBLoadResult lengthMismatch(String key, int found, int needed) {
            return new DBLoadResult(Status.LENGTH_MISMATCH, null, found, needed,
                    "CEC data found for '" + key + "' but has " + found
                    + " values; model requires " + needed + ".\n"
                    + "Check that cec.json lists exactly " + needed
                    + " cecTerms matching the cluster types for this model.", false);
        }
    }

    // -------------------------------------------------------------------------
    // Database load (silent, typed result — Bug 7 fix)
    // -------------------------------------------------------------------------

    /**
     * Attempts to load ECI data using the full model-qualified CEC key,
     * evaluating temperature-dependent terms at the given temperature.
     * Key: {elements}_{structure}_{phase}_{model}  e.g. "Nb-Ti_BCC_A2_T"
     */
    public static DBLoadResult loadECIFromDatabase(String elements, String structure,
                                                    String phase, String model,
                                                    double temperature,
                                                    int requiredLength) {
        System.out.println("[ECILoader.loadECIFromDatabase] key=" + elements
                + "_" + structure + "_" + phase + "_" + model
                + "  T=" + temperature + "K  required=" + requiredLength);
        Optional<double[]> cecOpt = SystemDataLoader.loadCecValuesAt(
                elements, structure, phase, model, temperature);
        return evaluateResult(elements + "_" + structure + "_" + phase + "_" + model,
                cecOpt, requiredLength, temperature);
    }

    /**
     * Attempts to load ECI data using the full model-qualified CEC key (legacy, no temperature).
     */
    public static DBLoadResult loadECIFromDatabase(String elements, String structure,
                                                    String phase, String model,
                                                    int requiredLength) {
        System.out.println("[ECILoader.loadECIFromDatabase] key=" + elements
                + "_" + structure + "_" + phase + "_" + model
                + "  required=" + requiredLength + "  (no temperature)");
        Optional<double[]> cecOpt = SystemDataLoader.loadCecValues(elements, structure, phase, model);
        return evaluateResult(elements + "_" + structure + "_" + phase + "_" + model,
                cecOpt, requiredLength);
    }

    /**
     * Attempts to load ECI data from the database only (legacy — no model qualifier).
     */
    public static DBLoadResult loadECIFromDatabase(String elements, int requiredLength) {
        System.out.println("[ECILoader.loadECIFromDatabase] elements=" + elements
                + "  required=" + requiredLength + "  (legacy, no model qualifier)");
        Optional<double[]> cecOpt = SystemDataLoader.loadCecValues(elements);
        return evaluateResult(elements, cecOpt, requiredLength);
    }

    private static DBLoadResult evaluateResult(String key, Optional<double[]> cecOpt, int required) {
        return evaluateResult(key, cecOpt, required, Double.NaN);
    }

    private static DBLoadResult evaluateResult(String key, Optional<double[]> cecOpt,
                                                int required, double temperature) {
        if (cecOpt.isEmpty()) return DBLoadResult.notFound(key, required);
        double[] eci = cecOpt.get();
        if (eci.length != required) return DBLoadResult.lengthMismatch(key, eci.length, required);
        if (!Double.isNaN(temperature)) return DBLoadResult.okTempEvaluated(eci, required, temperature);
        return DBLoadResult.ok(eci, required);
    }

    // -------------------------------------------------------------------------
    // Interactive load-or-input
    // -------------------------------------------------------------------------

    /**
     * Loads ECI using the model-qualified key at the given temperature; prompts user on failure.
     */
    public static Optional<double[]> loadOrInputECI(String elements, String structure,
                                                     String phase, String model,
                                                     double temperature,
                                                     int requiredLength) {
        DBLoadResult dbResult = loadECIFromDatabase(elements, structure, phase, model,
                temperature, requiredLength);
        return resolveOrPrompt(dbResult, requiredLength);
    }

    /**
     * Loads ECI using the model-qualified key (no temperature); prompts user on failure.
     */
    public static Optional<double[]> loadOrInputECI(String elements, String structure,
                                                     String phase, String model,
                                                     int requiredLength) {
        DBLoadResult dbResult = loadECIFromDatabase(elements, structure, phase, model, requiredLength);
        return resolveOrPrompt(dbResult, requiredLength);
    }

    /**
     * Loads ECI using the legacy element-only key; prompts user on failure.
     */
    public static Optional<double[]> loadOrInputECI(String elements, int requiredLength) {
        DBLoadResult dbResult = loadECIFromDatabase(elements, requiredLength);
        return resolveOrPrompt(dbResult, requiredLength);
    }

    /** Shared prompt logic for all loadOrInputECI overloads. */
    private static Optional<double[]> resolveOrPrompt(DBLoadResult dbResult, int requiredLength) {
        switch (dbResult.status) {
            case OK:
                return Optional.of(dbResult.eci);
            case NOT_FOUND: {
                boolean proceed = showWarningAndAsk("CEC Data Not Found",
                    dbResult.message + "\n\nProvide " + requiredLength
                    + " ECI values manually?");
                return proceed ? showECIInputDialog(requiredLength) : Optional.empty();
            }
            case LENGTH_MISMATCH: {
                boolean proceed = showWarningAndAsk("CEC Length Mismatch",
                    dbResult.message + "\n\nOverride with manual input ("
                    + requiredLength + " values)?");
                return proceed ? showECIInputDialog(requiredLength) : Optional.empty();
            }
            default: return Optional.empty();
        }
    }

    // -------------------------------------------------------------------------
    // Manual input dialog
    // -------------------------------------------------------------------------

    private static Optional<double[]> showECIInputDialog(int requiredLength) {
        Dialog<double[]> dialog = new Dialog<>();
        dialog.setTitle("ECI Manual Input");
        dialog.setHeaderText("Enter " + requiredLength + " ECI values for this system");

        ButtonType submitButton = new ButtonType("Submit", ButtonBar.ButtonData.OK_DONE);
        dialog.getDialogPane().getButtonTypes().addAll(submitButton, ButtonType.CANCEL);

        GridPane grid = new GridPane();
        grid.setHgap(10);
        grid.setVgap(10);
        grid.setPadding(new Insets(15));

        Label instructionLabel = new Label(
            "Enter exactly " + requiredLength + " ECI values separated by commas or spaces.\n"
            + "Order must match cluster types: empty, point, pairs, triplets, higher.\n"
            + "Example (5 values): 0.0, 0.0, -390.0, -260.0, 0.0"
        );
        instructionLabel.setWrapText(true);
        grid.add(instructionLabel, 0, 0);

        TextArea eciTextArea = new TextArea();
        eciTextArea.setPromptText("0.0, 0.0, -390.0, -260.0, 0.0");
        eciTextArea.setPrefRowCount(4);
        eciTextArea.setWrapText(true);
        GridPane.setHgrow(eciTextArea, Priority.ALWAYS);
        grid.add(eciTextArea, 0, 1);

        Label helpLabel = new Label("Comma or space separated decimal numbers");
        helpLabel.setStyle("-fx-font-size: 9; -fx-text-fill: #666;");
        grid.add(helpLabel, 0, 2);

        dialog.getDialogPane().setContent(grid);
        dialog.getDialogPane().setPrefWidth(470);

        dialog.setResultConverter(button -> {
            if (button != submitButton) return null;

            String input = eciTextArea.getText().trim();
            if (input.isEmpty()) {
                showError("Input Required", "Please enter ECI values before submitting.");
                return null;
            }
            try {
                double[] eci = parseECIString(input);
                if (eci.length != requiredLength) {
                    showError("Length Mismatch",
                        "Expected " + requiredLength + " values but got " + eci.length + ".\n"
                        + "Please provide exactly " + requiredLength + " values.");
                    return null;
                }
                return eci;
            } catch (NumberFormatException ex) {
                showError("Invalid Format",
                    "Could not parse ECI values: " + ex.getMessage() + "\n"
                    + "Use decimal numbers separated by commas or spaces.");
                return null;
            }
        });

        dialog.showAndWait();
        return dialog.getResult() != null ? Optional.of(dialog.getResult()) : Optional.empty();
    }

    // -------------------------------------------------------------------------
    // Helpers
    // -------------------------------------------------------------------------

    private static double[] parseECIString(String input) {
        String[] parts = input.replace(",", " ").trim().split("\\s+");
        double[] values = new double[parts.length];
        for (int i = 0; i < parts.length; i++) {
            values[i] = Double.parseDouble(parts[i].trim());
        }
        return values;
    }

    private static boolean showWarningAndAsk(String title, String message) {
        Alert alert = new Alert(Alert.AlertType.WARNING);
        alert.setTitle(title);
        alert.setHeaderText(null);
        alert.setContentText(message);
        alert.getDialogPane().setPrefWidth(480);
        Optional<ButtonType> result = alert.showAndWait();
        return result.isPresent() && result.get() == ButtonType.OK;
    }

    private static void showError(String title, String message) {
        Alert alert = new Alert(Alert.AlertType.ERROR);
        alert.setTitle(title);
        alert.setHeaderText(null);
        alert.setContentText(message);
        alert.showAndWait();
    }
}
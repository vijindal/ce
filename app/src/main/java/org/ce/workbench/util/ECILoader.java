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
 * ECI can come from:
 * 1. Computed CEC (Cluster Expansion Coefficients) from database
 * 2. User-provided array in manual input dialog
 */
public class ECILoader {
    
    /**
     * Attempts to load ECI data from database only (silent, no prompts).
     * 
     * @param elements Element specification (e.g., "Ti-Nb", "Fe-Cr")
     * @param requiredLength Expected length of ECI array (from cluster type count)
     * @return Optional containing the ECI array, or empty if not found or mismatch
     */
    public static Optional<double[]> loadECIFromDatabase(String elements, int requiredLength) {
        Optional<double[]> cecData = SystemDataLoader.loadCecValues(elements);
        
        if (cecData.isPresent()) {
            double[] eci = cecData.get();
            if (eci.length == requiredLength) {
                return Optional.of(eci);
            }
            // Length mismatch - return empty (don't prompt)
            return Optional.empty();
        }
        
        // CEC not found in database
        return Optional.empty();
    }
    
    /**
     * Attempts to load ECI data from database or prompts user for manual input.
     * 
     * @param elements Element specification (e.g., "Ti-Nb", "Fe-Cr")
     * @param requiredLength Expected length of ECI array (from cluster type count)
     * @return Optional containing the ECI array, or empty if cancelled
     */
    public static Optional<double[]> loadOrInputECI(String elements, int requiredLength) {
        // First, try to load CEC data silently
        Optional<double[]> cecData = SystemDataLoader.loadCecValues(elements);
        
        if (cecData.isPresent()) {
            double[] eci = cecData.get();
            if (eci.length == requiredLength) {
                return Optional.of(eci);
            } else {
                // CEC exists but wrong length - warn user and offer manual input
                boolean useManual = showWarningAndAsk(
                    "CEC Length Mismatch",
                    "CEC data found for " + elements + " but has " + eci.length + 
                    " values instead of expected " + requiredLength + ".\n" +
                    "Do you want to provide manual ECI input?"
                );
                if (useManual) {
                    return showECIInputDialog(requiredLength);
                }
                return Optional.empty();
            }
        } else {
            // CEC not available - show manual input dialog
            boolean showDialog = showWarningAndAsk(
                "CEC Data Not Found",
                "No CEC (cluster expansion coefficients) found for " + elements + ".\n" +
                "Do you want to provide manual ECI (effective cluster interactions) input?\n" +
                "Required: " + requiredLength + " values"
            );
            if (showDialog) {
                return showECIInputDialog(requiredLength);
            }
            return Optional.empty();
        }
    }
    
    /**
     * Shows a dialog for manual ECI input.
     * 
     * @param requiredLength Expected number of ECI values
     * @return Optional containing the ECI array, or empty if cancelled
     */
    private static Optional<double[]> showECIInputDialog(int requiredLength) {
        Dialog<double[]> dialog = new Dialog<>();
        dialog.setTitle("ECI Input");
        dialog.setHeaderText("Provide " + requiredLength + " ECI values (comma or space separated)");
        
        ButtonType submitButton = new ButtonType("Submit", ButtonBar.ButtonData.OK_DONE);
        dialog.getDialogPane().getButtonTypes().addAll(submitButton, ButtonType.CANCEL);
        
        GridPane grid = new GridPane();
        grid.setHgap(10);
        grid.setVgap(10);
        grid.setPadding(new Insets(15));
        
        Label instructionLabel = new Label(
            "Enter " + requiredLength + " ECI values separated by commas or spaces.\n" +
            "Example: 0.1, -0.05, 0.02, -0.01"
        );
        instructionLabel.setWrapText(true);
        grid.add(instructionLabel, 0, 0);
        
        TextArea eciTextArea = new TextArea();
        eciTextArea.setPromptText("0.1, -0.05, 0.02, -0.01, ...");
        eciTextArea.setPrefRowCount(4);
        eciTextArea.setWrapText(true);
        GridPane.setHgrow(eciTextArea, Priority.ALWAYS);
        grid.add(eciTextArea, 0, 1);
        
        Label helpLabel = new Label("Format: Comma or space separated decimal numbers");
        helpLabel.setStyle("-fx-font-size: 9; -fx-text-fill: #666;");
        grid.add(helpLabel, 0, 2);
        
        dialog.getDialogPane().setContent(grid);
        dialog.getDialogPane().setPrefWidth(450);
        
        dialog.setResultConverter(dialogButton -> {
            if (dialogButton == submitButton) {
                String input = eciTextArea.getText().trim();
                if (input.isEmpty()) {
                    showError("Input Required", "Please enter ECI values.");
                    return null;
                }
                
                try {
                    double[] eci = parseECIString(input);
                    if (eci.length != requiredLength) {
                        showError(
                            "Length Mismatch",
                            "Expected " + requiredLength + " values but got " + eci.length
                        );
                        return null;
                    }
                    return eci;
                } catch (NumberFormatException ex) {
                    showError("Invalid Format", "Could not parse ECI values: " + ex.getMessage());
                    return null;
                }
            }
            return null;
        });
        
        dialog.showAndWait();
        return dialog.getResult() != null ? Optional.of(dialog.getResult()) : Optional.empty();
    }
    
    /**
     * Parses a string containing comma or space-separated decimal numbers.
     * 
     * @param input String like "0.1, -0.05, 0.02" or "0.1 -0.05 0.02"
     * @return Array of parsed double values
     * @throws NumberFormatException if parsing fails
     */
    private static double[] parseECIString(String input) {
        // Replace commas with spaces for consistent splitting
        String normalized = input.replace(",", " ").trim();
        
        // Split by any whitespace and filter empty strings
        String[] parts = normalized.split("\\s+");
        double[] values = new double[parts.length];
        
        for (int i = 0; i < parts.length; i++) {
            values[i] = Double.parseDouble(parts[i]);
        }
        
        return values;
    }
    
    private static boolean showWarningAndAsk(String title, String message) {
        Alert alert = new Alert(Alert.AlertType.WARNING);
        alert.setTitle(title);
        alert.setHeaderText(null);
        alert.setContentText(message);
        
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

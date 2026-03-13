package org.ce.presentation.gui.component;

import javafx.geometry.Insets;
import javafx.scene.control.*;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.Priority;
import org.ce.domain.system.SystemIdentity;

import java.util.Optional;

/**
 * Standalone dialog that collects cluster-file and symmetry-group paths required
 * to start the CF identification pipeline.
 *
 * <p>Previously this logic lived as a private method ({@code showIdentificationDialog})
 * and an inner class ({@code IdentificationInput}) inside {@code SystemRegistryPanel},
 * and was duplicated across the <i>Create System</i> and <i>Create Cluster</i> code
 * paths.  Extracting it here removes that duplication and makes the dialog reusable
 * from any call site without coupling to the panel.</p>
 *
 * <h2>Usage</h2>
 * <pre>{@code
 * Optional<IdentificationInputDialog.Input> result =
 *         IdentificationInputDialog.show(system, "CF Identification");
 * result.ifPresent(input -> coordinator.createSystem(system, input));
 * }</pre>
 */
public final class IdentificationInputDialog {

    private IdentificationInputDialog() {}   // utility class — not instantiated

    // -----------------------------------------------------------------------
    // Public API
    // -----------------------------------------------------------------------

    /**
     * Shows the modal dialog and blocks until the user dismisses it.
     *
     * @param system system whose pre-resolved cluster file and symmetry group
     *               are used to pre-fill the form fields
     * @param title  dialog window title (e.g. {@code "CF Identification"})
     * @return {@link Optional} containing the user input if Run was clicked,
     *         empty if the dialog was cancelled
     */
    public static Optional<Input> show(SystemIdentity system, String title) {
        Dialog<ButtonType> dialog = new Dialog<>();
        dialog.setTitle(title);
        dialog.setHeaderText("Provide cluster files and symmetry groups for " + system.getName());

        ButtonType runButton = new ButtonType("Run", ButtonBar.ButtonData.OK_DONE);
        dialog.getDialogPane().getButtonTypes().addAll(runButton, ButtonType.CANCEL);

        GridPane grid = new GridPane();
        grid.setHgap(10);
        grid.setVgap(10);
        grid.setPadding(new Insets(10));

        // Pre-fill with values resolved from the system mapping
        TextField disClusterField = new TextField(nullToEmpty(system.getClusterFilePath()));
        TextField ordClusterField = new TextField(nullToEmpty(system.getClusterFilePath()));
        TextField disSymField     = new TextField(nullToEmpty(system.getSymmetryGroupName()));
        TextField ordSymField     = new TextField(nullToEmpty(system.getSymmetryGroupName()));

        addRow(grid, 0, "Disordered cluster file",   disClusterField);
        addRow(grid, 1, "Ordered cluster file",       ordClusterField);
        addRow(grid, 2, "Disordered symmetry group",  disSymField);
        addRow(grid, 3, "Ordered symmetry group",     ordSymField);

        dialog.getDialogPane().setContent(grid);
        dialog.getDialogPane().setPrefWidth(520);

        Optional<ButtonType> result = dialog.showAndWait();
        if (result.isEmpty() || result.get() != runButton) {
            return Optional.empty();
        }

        String disCluster = disClusterField.getText().trim();
        String ordCluster = ordClusterField.getText().trim();
        String disSym     = disSymField.getText().trim();
        String ordSym     = ordSymField.getText().trim();

        if (disCluster.isEmpty() || ordCluster.isEmpty() || disSym.isEmpty() || ordSym.isEmpty()) {
            showAlert("Missing input", "All fields are required to start identification.");
            return Optional.empty();
        }

        return Optional.of(new Input(disCluster, ordCluster, disSym, ordSym));
    }

    // -----------------------------------------------------------------------
    // Input record
    // -----------------------------------------------------------------------

    /**
     * Immutable value object holding the four paths entered by the user.
     */
    public record Input(
            String disorderedClusterFile,
            String orderedClusterFile,
            String disorderedSymmetryGroup,
            String orderedSymmetryGroup
    ) {}

    // -----------------------------------------------------------------------
    // Private helpers
    // -----------------------------------------------------------------------

    private static void addRow(GridPane grid, int row, String labelText, TextField field) {
        Label label = new Label(labelText);
        field.setPromptText(labelText);
        field.setMaxWidth(Double.MAX_VALUE);
        GridPane.setHgrow(field, Priority.ALWAYS);
        grid.add(label, 0, row);
        grid.add(field, 1, row);
    }

    private static String nullToEmpty(String value) {
        return value == null ? "" : value;
    }

    private static void showAlert(String title, String message) {
        Alert alert = new Alert(Alert.AlertType.INFORMATION);
        alert.setTitle(title);
        alert.setHeaderText(null);
        alert.setContentText(message);
        alert.showAndWait();
    }
}

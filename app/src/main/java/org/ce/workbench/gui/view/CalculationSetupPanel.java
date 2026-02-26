package org.ce.workbench.gui.view;

import javafx.geometry.Insets;
import javafx.scene.control.*;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;
import org.ce.workbench.backend.registry.SystemRegistry;
import org.ce.workbench.gui.model.SystemInfo;

import java.util.Comparator;

/**
 * Calculation setup panel with system selection and MCS/CVM inputs.
 */
public class CalculationSetupPanel extends VBox {

    private final SystemRegistry registry;

    private final ComboBox<SystemInfo> systemSelector;
    private final RadioButton mcsToggle;
    private final RadioButton cvmToggle;

    public CalculationSetupPanel(SystemRegistry registry) {
        this.registry = registry;

        setSpacing(12);
        setPadding(new Insets(15));

        Label title = new Label("Calculation Setup");
        title.setStyle("-fx-font-size: 14; -fx-font-weight: bold;");

        // System selection
        Label systemLabel = new Label("System");
        systemSelector = new ComboBox<>();
        systemSelector.setPromptText("Select a registered system");
        systemSelector.setMaxWidth(Double.MAX_VALUE);
        refreshSystemList();

        HBox systemRow = new HBox(10, systemLabel, systemSelector);
        systemRow.setFillHeight(true);
        HBox.setHgrow(systemSelector, Priority.ALWAYS);

        // Calculation type
        Label calcTypeLabel = new Label("Calculation Type");
        ToggleGroup calcTypeGroup = new ToggleGroup();
        mcsToggle = new RadioButton("MCS (Monte Carlo)");
        cvmToggle = new RadioButton("CVM (Cluster Variation)");
        mcsToggle.setToggleGroup(calcTypeGroup);
        cvmToggle.setToggleGroup(calcTypeGroup);
        mcsToggle.setSelected(true);

        HBox calcTypeRow = new HBox(15, calcTypeLabel, mcsToggle, cvmToggle);

        // General parameters
        VBox generalSection = new VBox(8);
        Label generalLabel = new Label("General Parameters");
        generalLabel.setStyle("-fx-font-weight: bold;");
        generalSection.getChildren().addAll(generalLabel, buildGeneralGrid());

        // MCS parameters
        VBox mcsSection = new VBox(8);
        Label mcsLabel = new Label("MCS Parameters");
        mcsLabel.setStyle("-fx-font-weight: bold;");
        mcsSection.getChildren().addAll(mcsLabel, buildMcsGrid());

        // CVM parameters
        VBox cvmSection = new VBox(8);
        Label cvmLabel = new Label("CVM Parameters");
        cvmLabel.setStyle("-fx-font-weight: bold;");
        cvmSection.getChildren().addAll(cvmLabel, buildCvmGrid());

        // Action buttons
        Button runButton = new Button("Run Calculation");
        runButton.setStyle("-fx-font-weight: bold;");
        Button resetButton = new Button("Reset");
        HBox actionRow = new HBox(10, runButton, resetButton);

        getChildren().addAll(
            title,
            new Separator(),
            systemRow,
            calcTypeRow,
            new Separator(),
            generalSection,
            new Separator(),
            mcsSection,
            cvmSection,
            new Separator(),
            actionRow
        );
    }

    private GridPane buildGeneralGrid() {
        GridPane grid = baseGrid();

        TextField temperatureField = new TextField("800");
        TextField compositionField = new TextField("0.5");
        TextField supercellField = new TextField("10");
        TextField stepsField = new TextField("20000");

        addRow(grid, 0, "Temperature (K)", temperatureField);
        addRow(grid, 1, "Composition (x)", compositionField);
        addRow(grid, 2, "Supercell Size", supercellField);
        addRow(grid, 3, "Total Steps", stepsField);

        return grid;
    }

    private GridPane buildMcsGrid() {
        GridPane grid = baseGrid();

        TextField equilibrationField = new TextField("5000");
        TextField averagingField = new TextField("10000");
        TextField seedField = new TextField("42");

        addRow(grid, 0, "Equilibration Steps", equilibrationField);
        addRow(grid, 1, "Averaging Steps", averagingField);
        addRow(grid, 2, "Random Seed", seedField);

        return grid;
    }

    private GridPane buildCvmGrid() {
        GridPane grid = baseGrid();

        TextField maxClusterField = new TextField("4");
        TextField toleranceField = new TextField("1e-6");

        addRow(grid, 0, "Max Cluster Size", maxClusterField);
        addRow(grid, 1, "Tolerance", toleranceField);

        return grid;
    }

    private GridPane baseGrid() {
        GridPane grid = new GridPane();
        grid.setHgap(10);
        grid.setVgap(8);
        grid.setPadding(new Insets(5, 0, 5, 0));
        return grid;
    }

    private void addRow(GridPane grid, int row, String labelText, Control field) {
        Label label = new Label(labelText);
        field.setMaxWidth(Double.MAX_VALUE);
        GridPane.setHgrow(field, Priority.ALWAYS);
        grid.add(label, 0, row);
        grid.add(field, 1, row);
    }

    private void refreshSystemList() {
        systemSelector.getItems().clear();
        registry.getAllSystems().stream()
            .sorted(Comparator.comparing(SystemInfo::getName))
            .forEach(systemSelector.getItems()::add);
    }
}

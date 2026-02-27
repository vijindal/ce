package org.ce.workbench.gui.view;

import javafx.geometry.Insets;
import javafx.scene.control.*;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;
import org.ce.workbench.backend.registry.SystemRegistry;
import org.ce.workbench.backend.job.BackgroundJobManager;
import org.ce.workbench.backend.job.ClusterIdentificationJob;
import org.ce.workbench.gui.model.SystemInfo;
import org.ce.workbench.util.CalculationContext;
import org.ce.workbench.util.ECILoader;
import org.ce.workbench.util.MCSExecutor;
import org.ce.workbench.util.ClusterDataCache;
import org.ce.identification.engine.ClusCoordListResult;

import java.util.Comparator;
import java.util.Optional;

/**
 * Calculation setup panel with system selection and MCS/CVM inputs.
 * Shows either MCS or CVM parameters based on calculation type selection.
 */
public class CalculationSetupPanel extends VBox {

    private final SystemRegistry registry;
    private final ResultsPanel resultsPanel;
    private final BackgroundJobManager jobManager;
    private final RadioButton mcsToggle;
    private final RadioButton cvmToggle;
    private final VBox parametersContainer;
    
    // Common parameters
    private final TextField temperatureField;
    private final TextField compositionField;
    
    // MCS parameters
    private final TextField mcsEquilibrationField;
    private final TextField mcsAveragingField;
    private final TextField mcsSeedField;
    
    // CVM parameters
    private final TextField cvmMaxClusterField;
    private final TextField cvmToleranceField;

    public CalculationSetupPanel(SystemRegistry registry, BackgroundJobManager jobManager, ResultsPanel resultsPanel) {
        this.registry = registry;
        this.jobManager = jobManager;
        this.resultsPanel = resultsPanel;

        setSpacing(8);
        setPadding(new Insets(12));
        setStyle("-fx-border-color: #e0e0e0; -fx-border-width: 1;");
        setPrefHeight(300);

        // Title
        Label title = new Label("Calculation Setup");
        title.setStyle("-fx-font-size: 12; -fx-font-weight: bold;");

        // Calculation type toggle
        Label calcTypeLabel = new Label("Type");
        ToggleGroup calcTypeGroup = new ToggleGroup();
        mcsToggle = new RadioButton("MCS");
        cvmToggle = new RadioButton("CVM");
        mcsToggle.setToggleGroup(calcTypeGroup);
        cvmToggle.setToggleGroup(calcTypeGroup);
        mcsToggle.setSelected(true);
        mcsToggle.setStyle("-fx-font-size: 10;");
        cvmToggle.setStyle("-fx-font-size: 10;");

        HBox calcTypeRow = new HBox(15, calcTypeLabel, mcsToggle, cvmToggle);
        calcTypeLabel.setMinWidth(70);

        // Initialize common parameters
        temperatureField = new TextField("800");
        compositionField = new TextField("0.5");
        
        // Initialize MCS parameters
        mcsEquilibrationField = new TextField("5000");
        mcsAveragingField = new TextField("10000");
        mcsSeedField = new TextField("42");
        
        // Initialize CVM parameters
        cvmMaxClusterField = new TextField("4");
        cvmToleranceField = new TextField("1e-6");

        // Build common parameters section
        VBox commonSection = buildCommonParametersSection();

        // Build parameters container - will hold either MCS or CVM section
        parametersContainer = new VBox(6);
        parametersContainer.setPadding(new Insets(5, 0, 0, 0));
        updateParametersDisplay();

        // Toggle handler to switch between MCS and CVM
        mcsToggle.selectedProperty().addListener((obs, oldVal, newVal) -> updateParametersDisplay());
        cvmToggle.selectedProperty().addListener((obs, oldVal, newVal) -> updateParametersDisplay());

        // Action buttons
        HBox actionRow = buildActionButtons();

        // Add all components
        getChildren().addAll(
            title,
            calcTypeRow,
            new Separator(),
            commonSection,
            parametersContainer,
            new Separator(),
            actionRow
        );
        
        VBox.setVgrow(parametersContainer, Priority.SOMETIMES);
    }

    private VBox buildCommonParametersSection() {
        VBox section = new VBox(4);
        Label label = new Label("Common");
        label.setStyle("-fx-font-size: 10; -fx-font-weight: bold;");
        
        GridPane grid = new GridPane();
        grid.setHgap(8);
        grid.setVgap(6);
        grid.setPadding(new Insets(0));
        
        temperatureField.setPrefHeight(22);
        compositionField.setPrefHeight(22);
        
        addCompactRow(grid, 0, "Temperature (K)", temperatureField);
        addCompactRow(grid, 1, "Composition (x)", compositionField);
        
        section.getChildren().addAll(label, grid);
        return section;
    }

    private VBox buildMcsParametersSection() {
        VBox section = new VBox(4);
        Label label = new Label("MCS Parameters");
        label.setStyle("-fx-font-size: 10; -fx-font-weight: bold;");
        
        GridPane grid = new GridPane();
        grid.setHgap(8);
        grid.setVgap(6);
        grid.setPadding(new Insets(0));
        
        mcsEquilibrationField.setPrefHeight(22);
        mcsAveragingField.setPrefHeight(22);
        mcsSeedField.setPrefHeight(22);
        
        addCompactRow(grid, 0, "Equilibration", mcsEquilibrationField);
        addCompactRow(grid, 1, "Averaging", mcsAveragingField);
        addCompactRow(grid, 2, "Seed", mcsSeedField);
        
        section.getChildren().addAll(label, grid);
        return section;
    }

    private VBox buildCvmParametersSection() {
        VBox section = new VBox(4);
        Label label = new Label("CVM Parameters");
        label.setStyle("-fx-font-size: 10; -fx-font-weight: bold;");
        
        GridPane grid = new GridPane();
        grid.setHgap(8);
        grid.setVgap(6);
        grid.setPadding(new Insets(0));
        
        cvmMaxClusterField.setPrefHeight(22);
        cvmToleranceField.setPrefHeight(22);
        
        addCompactRow(grid, 0, "Max Cluster", cvmMaxClusterField);
        addCompactRow(grid, 1, "Tolerance", cvmToleranceField);
        
        section.getChildren().addAll(label, grid);
        return section;
    }

    private HBox buildActionButtons() {
        Button runButton = new Button("Run");
        Button resetButton = new Button("Reset");
        runButton.setStyle("-fx-font-size: 10; -fx-padding: 6 20;");
        resetButton.setStyle("-fx-font-size: 10; -fx-padding: 6 20;");
        
        // Run button handler - execute MCS or CVM
        runButton.setOnAction(e -> {
            if (mcsToggle.isSelected()) {
                runMCSCalculation();
            } else {
                runCVMCalculation();
            }
        });
        
        // Reset button handler
        resetButton.setOnAction(e -> {
            temperatureField.setText("800");
            compositionField.setText("0.5");
            mcsEquilibrationField.setText("5000");
            mcsAveragingField.setText("10000");
            mcsSeedField.setText("42");
            cvmMaxClusterField.setText("4");
            cvmToleranceField.setText("1e-6");
        });
        
        HBox row = new HBox(8, runButton, resetButton);
        return row;
    }
    
    private void runMCSCalculation() {
        // Get the most recently created system from registry
        SystemInfo selectedSystem = registry.getAllSystems().stream()
            .max((s1, s2) -> s1.getClustersComputedDate().compareTo(s2.getClustersComputedDate()))
            .orElse(null);
        
        if (selectedSystem == null) {
            showError("No System Available", "Please create a system first in the System Setup section.");
            return;
        }
        
        if (!selectedSystem.isClustersComputed() || !selectedSystem.isCfsComputed()) {
            showError(
                "Data Missing",
                "The system '" + selectedSystem.getName() + "' does not have cluster and CF data computed.\n" +
                "Please complete the identification pipeline first."
            );
            return;
        }
        
        // Parse parameters
        double temperature;
        double composition;
        int equilibration;
        int averaging;
        long seed;
        
        try {
            temperature = Double.parseDouble(temperatureField.getText().trim());
            composition = Double.parseDouble(compositionField.getText().trim());
            equilibration = Integer.parseInt(mcsEquilibrationField.getText().trim());
            averaging = Integer.parseInt(mcsAveragingField.getText().trim());
            seed = Long.parseLong(mcsSeedField.getText().trim());
            
            if (temperature <= 0) throw new NumberFormatException("Temperature must be positive");
            if (composition < 0 || composition > 1) throw new NumberFormatException("Composition must be between 0 and 1");
            if (equilibration <= 0) throw new NumberFormatException("Equilibration steps must be positive");
            if (averaging <= 0) throw new NumberFormatException("Averaging steps must be positive");
            
        } catch (NumberFormatException ex) {
            showError("Invalid Parameter", "Parameter parsing failed: " + ex.getMessage());
            return;
        }
        
        resultsPanel.logMessage("\n>>> MCS Calculation Requested");
        resultsPanel.logMessage("System: " + selectedSystem.getName());
        resultsPanel.logMessage("Parameters validated. Loading required data...");
        
        // Create calculation context
        CalculationContext context = new CalculationContext(
            selectedSystem,
            temperature,
            composition,
            4,  // Supercell size - could be parameterized
            equilibration,
            averaging,
            seed
        );
        
        // Load cluster data from the identification job (must be same session)
        String clusterJobId = selectedSystem.getClusterJobId();
        if (clusterJobId == null) {
            showError("No Cluster Identification", "System has no cluster identification job ID. Please run identification first.");
            return;
        }
        
        resultsPanel.logMessage("Retrieving cluster data from job: " + clusterJobId);
        ClusterIdentificationJob clusterJob = (ClusterIdentificationJob) jobManager.getJob(clusterJobId);
        if (clusterJob == null) {
            showError("Job Not Available", 
                "Cluster identification job no longer in memory.\n\n" +
                "Note: Cluster data is only available during the same session as identification.\n" +
                "Please run identification again if needed.");
            return;
        }
        
        ClusCoordListResult clusterData = clusterJob.getResult();
        if (clusterData == null) {
            showError("No Cluster Result", "Cluster identification job has no result. Please complete identification first.");
            return;
        }
        
        context.setClusterData(clusterData);
        resultsPanel.logMessage("✓ Cluster data loaded: " + clusterData.getTc() + " cluster types");
        
        // Load ECI data
        String elementsStr = String.join("-", selectedSystem.getComponents());
        resultsPanel.logMessage("Loading ECI data for " + elementsStr + "...");
        
        // Get actual cluster type count from cluster data
        int requiredECILength = context.getClusterData().getTc();
        
        // Try to load from database silently first
        Optional<double[]> eciOpt = ECILoader.loadECIFromDatabase(elementsStr, requiredECILength);
        
        if (eciOpt.isEmpty()) {
            // Database load failed, try with user prompt
            resultsPanel.logMessage("⚠ CEC not found in database, attempting manual load...");
            eciOpt = ECILoader.loadOrInputECI(elementsStr, requiredECILength);
        }
        
        if (eciOpt.isEmpty()) {
            resultsPanel.logMessage("⚠ ECI loading cancelled or failed.");
            return;
        }
        
        context.setECI(eciOpt.get());
        resultsPanel.logMessage("✓ ECI loaded: " + eciOpt.get().length + " values");
        
        // Validate context is ready
        if (!context.isReady()) {
            showError("Calculation Not Ready", "Missing cluster data or ECI. Cannot proceed with calculation.");
            return;
        }
        
        // Execute MCS calculation
        MCSExecutor.executeMCS(context, resultsPanel);
    }
    
    private void runCVMCalculation() {
        resultsPanel.logMessage("\n>>> CVM Calculation Requested");
        resultsPanel.logMessage("⚠ CVM calculation not yet implemented.");
    }
    
    private void showError(String title, String message) {
        Alert alert = new Alert(Alert.AlertType.ERROR);
        alert.setTitle(title);
        alert.setHeaderText(null);
        alert.setContentText(message);
        alert.showAndWait();
    }

    private void updateParametersDisplay() {
        parametersContainer.getChildren().clear();
        if (mcsToggle.isSelected()) {
            parametersContainer.getChildren().add(buildMcsParametersSection());
        } else {
            parametersContainer.getChildren().add(buildCvmParametersSection());
        }
    }

    private void addCompactRow(GridPane grid, int row, String labelText, Control field) {
        Label label = new Label(labelText);
        label.setStyle("-fx-font-size: 9;");
        field.setMaxWidth(Double.MAX_VALUE);
        field.setStyle("-fx-font-size: 9;");
        GridPane.setHgrow(field, Priority.ALWAYS);
        grid.add(label, 0, row);
        grid.add(field, 1, row);
    }
}

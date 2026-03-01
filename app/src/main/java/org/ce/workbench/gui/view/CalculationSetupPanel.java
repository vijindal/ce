package org.ce.workbench.gui.view;

import javafx.geometry.Insets;
import javafx.scene.control.*;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;
import org.ce.workbench.backend.registry.SystemRegistry;
import org.ce.workbench.backend.job.BackgroundJobManager;
import org.ce.workbench.gui.model.SystemInfo;
import org.ce.workbench.util.cache.AllClusterDataCache;
import org.ce.workbench.util.context.CVMCalculationContext;
import org.ce.workbench.util.context.CalculationContext;
import org.ce.workbench.util.cache.ClusterDataCache;
import org.ce.workbench.util.eci.ECILoader;
import org.ce.workbench.util.key.KeyUtils;
import org.ce.workbench.util.mcs.MCSExecutor;
import org.ce.workbench.backend.data.AllClusterData;
import org.ce.identification.result.ClusCoordListResult;

import java.util.Comparator;
import java.util.Optional;
import java.util.concurrent.ThreadLocalRandom;

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
    private final TextField mcsSupercellSizeField;
    private final TextField mcsEquilibrationField;
    private final TextField mcsAveragingField;
    
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
        mcsSupercellSizeField = new TextField("4");
        mcsEquilibrationField = new TextField("5000");
        mcsAveragingField = new TextField("10000");
        
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
        
        mcsSupercellSizeField.setPrefHeight(22);
        mcsEquilibrationField.setPrefHeight(22);
        mcsAveragingField.setPrefHeight(22);
        
        addCompactRow(grid, 0, "Supercell Size (L)", mcsSupercellSizeField);
        addCompactRow(grid, 1, "Equilibration", mcsEquilibrationField);
        addCompactRow(grid, 2, "Averaging", mcsAveragingField);
        
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
            mcsSupercellSizeField.setText("4");
            mcsEquilibrationField.setText("5000");
            mcsAveragingField.setText("10000");
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
        
        if (!selectedSystem.isClustersComputed()) {
            showError(
                "Data Missing",
                "The system '" + selectedSystem.getName() + "' does not have cluster data computed.\n" +
                "Please complete the identification pipeline first."
            );
            return;
        }
        
        // Parse parameters
        double temperature;
        double composition;
        int supercellSize;
        int equilibration;
        int averaging;
        
        try {
            temperature = Double.parseDouble(temperatureField.getText().trim());
            composition = Double.parseDouble(compositionField.getText().trim());
            supercellSize = Integer.parseInt(mcsSupercellSizeField.getText().trim());
            equilibration = Integer.parseInt(mcsEquilibrationField.getText().trim());
            averaging = Integer.parseInt(mcsAveragingField.getText().trim());
            
            if (temperature <= 0) throw new NumberFormatException("Temperature must be positive");
            if (composition < 0 || composition > 1) throw new NumberFormatException("Composition must be between 0 and 1");
            if (supercellSize < 1) throw new NumberFormatException("Supercell size must be >= 1");
            if (equilibration <= 0) throw new NumberFormatException("Equilibration steps must be positive");
            if (averaging <= 0) throw new NumberFormatException("Averaging steps must be positive");
            
        } catch (NumberFormatException ex) {
            showError("Invalid Parameter", "Parameter parsing failed: " + ex.getMessage());
            return;
        }

        long seed = ThreadLocalRandom.current().nextLong(1, Long.MAX_VALUE);
        
        resultsPanel.logMessage("\n>>> MCS Calculation Requested");
        resultsPanel.logMessage("System: " + selectedSystem.getName());
        resultsPanel.logMessage("Seed (auto): " + seed);
        resultsPanel.logMessage("Parameters validated. Loading required data...");
        
        // Create calculation context
        CalculationContext context = new CalculationContext(
            selectedSystem,
            temperature,
            composition,
            supercellSize,  // User-defined supercell size
            equilibration,
            averaging,
            seed
        );
        
        // Derive the two keys from the selected system
        String elementsStr     = String.join("-", selectedSystem.getComponents());
        String componentSuffix = KeyUtils.componentSuffix(selectedSystem.getNumComponents());
        // CEC key:     elements + model  e.g. "Nb-Ti_BCC_A2_T"
        String cecKey     = KeyUtils.cecKey(selectedSystem);
        // Cluster key: component-count + model  e.g. "BCC_A2_T_bin"
        String clusterKey = KeyUtils.clusterKey(selectedSystem);

        resultsPanel.logMessage("[MCS] CEC key     : " + cecKey);
        resultsPanel.logMessage("[MCS] Cluster key : " + clusterKey);

        // --- Load cluster data ---
        resultsPanel.logMessage("[MCS] Loading cluster data from cache...");
        ClusCoordListResult clusterData;
        try {
            java.util.Optional<ClusCoordListResult> cached =
                    ClusterDataCache.loadClusterData(clusterKey);
            if (cached.isEmpty()) {
                showError("Cluster Data Not Found",
                    "No valid cluster data found for '" + clusterKey + "'.\n\n"
                    + "The identification pipeline has not been run for this "
                    + componentSuffix + " " + selectedSystem.getStructure()
                    + "_" + selectedSystem.getPhase() + " system.\n\n"
                    + "Delete this system, recreate it, and run identification.\n"
                    + "Check console for [ClusterDataCache] messages for the exact path.");
                return;
            }
            clusterData = cached.get();
        } catch (Exception ex) {
            showError("Cluster Data Load Error",
                "Failed to load cluster data for '" + clusterKey + "':\n" + ex.getMessage());
            return;
        }
        context.setClusterData(clusterData);
        resultsPanel.logMessage("✓ Cluster data loaded: tc=" + clusterData.getTc()
                + "  orbitList=" + clusterData.getOrbitList().size());

        // --- Load ECI/CEC data using the full CEC key ---
        // ECI array must have one value for each cluster type (including excluded ones with value 0)
        int requiredECILength = clusterData.getTc();
        resultsPanel.logMessage("[MCS] Loading CEC  key=" + cecKey
                + "  required length=" + requiredECILength);

        ECILoader.DBLoadResult dbResult =
                ECILoader.loadECIFromDatabase(elementsStr,
                        selectedSystem.getStructure(),
                        selectedSystem.getPhase(),
                        selectedSystem.getModel(),
                        temperature,
                        requiredECILength);
        Optional<double[]> eciOpt;

        if (dbResult.status == ECILoader.DBLoadResult.Status.OK) {
            eciOpt = Optional.of(dbResult.eci);
            resultsPanel.logMessage("✓ CEC loaded from database: " + dbResult.message);
            if (dbResult.temperatureEvaluated) {
                resultsPanel.logMessage("  (T-dependent terms evaluated at T=" + temperature + "K)");
            }
        } else {
            resultsPanel.logMessage("⚠ CEC database load failed: " + dbResult.message);
            eciOpt = ECILoader.loadOrInputECI(elementsStr,
                    selectedSystem.getStructure(),
                    selectedSystem.getPhase(),
                    selectedSystem.getModel(),
                    temperature,
                    requiredECILength);
        }

        if (eciOpt.isEmpty()) {
            resultsPanel.logMessage("⚠ ECI loading cancelled or failed. Cannot run MCS.");
            return;
        }

        context.setECI(eciOpt.get());
        resultsPanel.logMessage("✓ ECI set: " + eciOpt.get().length + " values");

        if (!context.isReady()) {
            showError("ECI/Cluster Mismatch", context.getReadinessError());
            return;
        }

        // Execute MCS calculation on a background thread so UI/chart can update live
        Thread mcsThread = new Thread(() -> MCSExecutor.executeMCS(context, resultsPanel), "mcs-calculation-thread");
        mcsThread.setDaemon(true);
        mcsThread.start();
    }

    // getComponentSuffix() removed — use KeyUtils.componentSuffix() instead

    private void runCVMCalculation() {
        // Get the most recently created system from registry
        SystemInfo selectedSystem = registry.getAllSystems().stream()
            .max(Comparator.comparing(SystemInfo::getClustersComputedDate))
            .orElse(null);

        if (selectedSystem == null) {
            showError("No System Available", "Please create a system first in the System Setup section.");
            return;
        }

        if (!selectedSystem.isCfsComputed()) {
            showError(
                "Data Missing",
                "The system '" + selectedSystem.getName() + "' does not have CF data computed.\n" +
                "Please complete the identification pipeline first."
            );
            return;
        }

        // Parse parameters
        double temperature;
        double composition;
        double tolerance;

        try {
            temperature = Double.parseDouble(temperatureField.getText().trim());
            composition = Double.parseDouble(compositionField.getText().trim());
            tolerance   = Double.parseDouble(cvmToleranceField.getText().trim());

            if (temperature <= 0) throw new NumberFormatException("Temperature must be positive");
            if (composition < 0 || composition > 1) throw new NumberFormatException("Composition must be between 0 and 1");
            if (tolerance <= 0) throw new NumberFormatException("Tolerance must be positive");
        } catch (NumberFormatException ex) {
            showError("Invalid Parameter", "Parameter parsing failed: " + ex.getMessage());
            return;
        }

        resultsPanel.logMessage("\n>>> CVM Calculation Requested");
        resultsPanel.logMessage("System: " + selectedSystem.getName());
        resultsPanel.logMessage("Temperature: " + temperature + " K");
        resultsPanel.logMessage("Composition: " + composition);
        resultsPanel.logMessage("Tolerance: " + tolerance);
        resultsPanel.logMessage("Parameters validated. Loading required data...");

        String clusterKey = KeyUtils.clusterKey(selectedSystem);
        String cecKey     = KeyUtils.cecKey(selectedSystem);
        resultsPanel.logMessage("[CVM] CEC key     : " + cecKey);
        resultsPanel.logMessage("[CVM] Cluster key : " + clusterKey);

        // --- Load AllClusterData ---
        resultsPanel.logMessage("[CVM] Loading AllClusterData from cache...");
        AllClusterData allData;
        try {
            Optional<AllClusterData> cached = AllClusterDataCache.load(clusterKey);
            if (cached.isEmpty()) {
                showError("Cluster Data Not Found",
                    "No AllClusterData found for '" + clusterKey + "'.\n\n"
                    + "The identification pipeline may not have saved the complete data.\n"
                    + "Delete this system, recreate it, and run identification again.");
                return;
            }
            allData = cached.get();
        } catch (Exception ex) {
            showError("Cluster Data Load Error",
                "Failed to load AllClusterData for '" + clusterKey + "':\n" + ex.getMessage());
            return;
        }

        if (!allData.isComplete()) {
            showError("Incomplete Cluster Data",
                "AllClusterData for '" + clusterKey + "' is incomplete.\n\n"
                + allData.getCompletionStatus());
            return;
        }

        resultsPanel.logMessage("✓ AllClusterData loaded: " + allData);
        resultsPanel.logMessage("  Stage 1: tcdis=" + allData.getTcdis());
        resultsPanel.logMessage("  Stage 2: tcf=" + allData.getTcf());
        resultsPanel.logMessage("  Stage 3: C-matrix ready");

        // --- Load ECI/CEC data ---
        String elementsStr = String.join("-", selectedSystem.getComponents());
        int requiredECILength = allData.getStage1().getTc();
        resultsPanel.logMessage("[CVM] Loading CEC  key=" + cecKey
                + "  required length=" + requiredECILength);

        ECILoader.DBLoadResult dbResult =
                ECILoader.loadECIFromDatabase(elementsStr,
                        selectedSystem.getStructure(),
                        selectedSystem.getPhase(),
                        selectedSystem.getModel(),
                        temperature,
                        requiredECILength);
        Optional<double[]> eciOpt;

        if (dbResult.status == ECILoader.DBLoadResult.Status.OK) {
            eciOpt = Optional.of(dbResult.eci);
            resultsPanel.logMessage("✓ CEC loaded from database: " + dbResult.message);
            if (dbResult.temperatureEvaluated) {
                resultsPanel.logMessage("  (T-dependent terms evaluated at T=" + temperature + "K)");
            }
        } else {
            resultsPanel.logMessage("⚠ CEC database load failed: " + dbResult.message);
            eciOpt = ECILoader.loadOrInputECI(elementsStr,
                    selectedSystem.getStructure(),
                    selectedSystem.getPhase(),
                    selectedSystem.getModel(),
                    temperature,
                    requiredECILength);
        }

        if (eciOpt.isEmpty()) {
            resultsPanel.logMessage("⚠ ECI loading cancelled or failed. Cannot run CVM.");
            return;
        }

        // Build CVM context
        CVMCalculationContext context = new CVMCalculationContext(
                selectedSystem, temperature, composition, tolerance);
        context.setAllClusterData(allData);
        context.setECI(eciOpt.get());
        resultsPanel.logMessage("✓ ECI set: " + eciOpt.get().length + " values");

        if (!context.isReady()) {
            showError("CVM Context Not Ready", context.getReadinessError());
            return;
        }

        resultsPanel.logMessage("✓ CVM context is ready");
        resultsPanel.logMessage(context.getSummary());
        resultsPanel.logMessage("\n⚠ CVM NIM solver (Stage 4-5) not yet implemented.");
        resultsPanel.logMessage("  All prerequisite data has been loaded successfully.");
        resultsPanel.logMessage("  Next step: implement NIM free-energy minimization engine.");
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